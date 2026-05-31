package com.example.ocr.processing

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PaddleHtmlTableLedgerParser {

    private val dateRegex = Regex("""^(\d{1,2})[./](\d{1,2})$""")
    private val deliveryRegex = Regex("""^\d{1,4}\s*/\s*\d{1,2}$""")
    private val likelyDeliveryRegex = Regex("""^\d{3,5}$""") // like 12014, 4012

    fun parseJsonl(jsonlText: String?): LedgerPage {
         if (jsonlText.isNullOrBlank()) {
             return LedgerPage(customerNameRaw = null)
         }
         
         val lines = jsonlText.lines().filter { it.isNotBlank() }
         if (lines.isEmpty()) return LedgerPage(customerNameRaw = null)
         
         try {
             val root = JSONObject(lines[0])
             val result = root.optJSONObject("result") ?: return LedgerPage(customerNameRaw = null)
             val layoutParsingResults = result.optJSONArray("layoutParsingResults")
             
             if (layoutParsingResults != null) {
                  return parseLayoutParsingResults(layoutParsingResults)
             }
         } catch (e: Exception) {
             e.printStackTrace()
         }
         return LedgerPage(customerNameRaw = null)
    }
    
    fun parseLayoutParsingResults(results: JSONArray): LedgerPage {
        val rows = mutableListOf<LedgerRow>()
        val ignoredNoises = mutableListOf<IgnoredNoise>()
        var customerNameRaw: String? = null
        var parsedTableCount = 0
        
        fun extractFrom(json: Any?) {
            if (json is JSONArray) {
                for (i in 0 until json.length()) {
                    extractFrom(json.get(i))
                }
            } else if (json is JSONObject) {
                val prunedResult = json.optJSONObject("prunedResult")
                val parsingResList = prunedResult?.optJSONArray("parsing_res_list")
                if (parsingResList != null) {
                    for (j in 0 until parsingResList.length()) {
                        val res = parsingResList.optJSONObject(j) ?: continue
                        val blockLabel = res.optString("block_label")
                        if (blockLabel == "table") {
                            val html = res.optString("block_content")
                            if (html.isNotBlank()) {
                                parsedTableCount++
                                val page = parseHtmlTable(html)
                                if (customerNameRaw == null && page.customerNameRaw != null) {
                                    customerNameRaw = page.customerNameRaw
                                }
                                rows.addAll(page.rows)
                                ignoredNoises.addAll(page.ignoredNoises)
                            }
                        }
                    }
                } else {
                    val keys = json.keys()
                    while(keys.hasNext()) {
                        extractFrom(json.opt(keys.next()))
                    }
                }
            }
        }
        
        extractFrom(results)
        
        val page = LedgerPage(customerNameRaw = customerNameRaw, rows = rows, parsedTableCount = parsedTableCount, ignoredNoises = ignoredNoises)
        if (customerNameRaw != null) {
            val matchRes = CustomerMatcher.matchInternal(customerNameRaw)
            page.customerMatchStatus = matchRes.matchStatus
        }
        PageConsistencyValidator.validate(page)
        PageConfidenceScorer.evaluate(page)
        return page
    }
    
    fun parseHtmlTable(html: String): LedgerPage {
        val document: Document = Jsoup.parse(html)
        val grid = HtmlTableToGrid.parse(document)
        
        val rows = mutableListOf<LedgerRow>()
        val noises = mutableListOf<IgnoredNoise>()
        var customerNameRaw: String? = null
        
        // 1. Extract Customer Name
        val scanLimit = minOf(4, grid.size)
        for (r in 0 until scanLimit) {
            val fullText = grid[r].joinToString("")
            if (fullText.contains("酒店名称:") || fullText.contains("酒店名称：")) {
                val prefix = if (fullText.contains("酒店名称:")) "酒店名称:" else "酒店名称："
                val namePart = fullText.substringAfter(prefix).substringBefore("地址").substringBefore("电话").substringBefore("送出")
                    .replace("中餐", "")
                    .replace("汤锅", "")
                    .replace("签字", "")
                    .trim()
                if (namePart.isNotBlank()) {
                    customerNameRaw = namePart
                }
                break
            }
        }
        
        // 2. Fixed Template Processing + RowCellAligner
        val leftRows = mutableListOf<LedgerRow>()
        val rightRows = mutableListOf<LedgerRow>()
        
        for (r in 0 until grid.size) {
            val rowData = grid[r]
            val fullText = rowData.joinToString("")
            
            // Skip the header rows
            if (fullText.contains("酒店名称") || fullText.contains("地址") || fullText.contains("电话")) {
                continue
            }
            if (fullText.contains("送出") || fullText.contains("收回") || fullText.contains("破损") || fullText.contains("月日") || fullText.contains("日期")) {
                noises.add(IgnoredNoise(fullText, "HEADER_ROW", "Row $r"))
                continue
            }
            
            // Process LEFT: target range 0..4, but scan up to 5 to handle left shift
            val leftCandidate = findDateDeliveryPair(rowData, 0, 5)
            if (leftCandidate != null) {
                buildRow(leftCandidate.date, leftCandidate.delivery, "LEFT, Row $r", noises, "LEFT")?.let {
                    leftRows.add(it)
                }
                
                val startIndexForNoise = if (leftCandidate.deliveryIndex != -1) leftCandidate.deliveryIndex + 1 
                                         else if (leftCandidate.dateIndex != -1) leftCandidate.dateIndex + 1 
                                         else 1
                var cnt = 0
                for (i in startIndexForNoise until minOf(rowData.size, minOf(5, startIndexForNoise + 3))) {
                    val text = rowData[i].trim()
                    if (cnt == 0) checkAndAddNoise(text, "RETURN_COLUMN", "LEFT, Row $r, Col $i", noises)
                    else if (cnt == 1) checkAndAddNoise(text, "DAMAGE_COLUMN", "LEFT, Row $r, Col $i", noises)
                    else if (cnt == 2) checkAndAddNoise(text, "SIGNATURE", "LEFT, Row $r, Col $i", noises)
                    cnt++
                }
            } else {
                // If nothing found in candidate, we might just scan and log noises for 0..4
                for (i in 0 until minOf(rowData.size, 5)) {
                    val text = rowData[i].trim()
                    checkAndAddNoise(text, "UNKNOWN", "LEFT, Row $r, Col $i", noises)
                }
            }

            // Process RIGHT: target range 5..9, scan 5..rowData.size
            if (rowData.size > 5) {
                val rightCandidate = findDateDeliveryPair(rowData, 5, rowData.size)
                if (rightCandidate != null) {
                    buildRow(rightCandidate.date, rightCandidate.delivery, "RIGHT, Row $r", noises, "RIGHT")?.let {
                        rightRows.add(it)
                    }
                    
                    val startIndexForNoise = if (rightCandidate.deliveryIndex != -1) rightCandidate.deliveryIndex + 1 
                                             else if (rightCandidate.dateIndex != -1) rightCandidate.dateIndex + 1 
                                             else 6
                    var cnt = 0
                    for (i in startIndexForNoise until minOf(rowData.size, startIndexForNoise + 3)) {
                        val text = rowData[i].trim()
                        if (cnt == 0) checkAndAddNoise(text, "RETURN_COLUMN", "RIGHT, Row $r, Col $i", noises)
                        else if (cnt == 1) checkAndAddNoise(text, "DAMAGE_COLUMN", "RIGHT, Row $r, Col $i", noises)
                        else if (cnt == 2) checkAndAddNoise(text, "SIGNATURE", "RIGHT, Row $r, Col $i", noises)
                        cnt++
                    }
                } else {
                    for (i in 5 until rowData.size) {
                        val text = rowData[i].trim()
                        checkAndAddNoise(text, "UNKNOWN", "RIGHT, Row $r, Col $i", noises)
                    }
                }
            }
        }
        
        rows.addAll(leftRows)
        rows.addAll(rightRows)
        
        return LedgerPage(
            customerNameRaw = customerNameRaw,
            rows = rows,
            ignoredNoises = noises
        )
    }

    private fun checkAndAddNoise(text: String, reason: String, location: String, noises: MutableList<IgnoredNoise>) {
        if (text.isNotBlank()) {
            if (text.contains(Regex("""\d"""))) {
                noises.add(IgnoredNoise(text, reason, location))
            } else if (reason == "SIGNATURE" && text != "签字") {
                noises.add(IgnoredNoise(text, reason, location))
            }
        }
    }
    
    data class DateDeliveryCandidate(val date: String, val delivery: String, val dateIndex: Int, val deliveryIndex: Int)

    private fun isDeliveryCandidate(text: String): Boolean {
        if (isDeliveryFormat(text)) return true
        if (text.contains("*") || text.contains("x") || text.contains("×") || text.contains("。")) return true
        return false
    }

    private fun findDateDeliveryPair(cells: List<String>, startIndex: Int, endIndex: Int): DateDeliveryCandidate? {
        val actualEndIndex = minOf(endIndex, cells.size)
        
        // Try finding a date first
        for (i in startIndex until actualEndIndex) {
            val cell1 = cells[i].trim()
            if (isValidDate(cell1)) {
                // Check next 1-2 cells for delivery
                if (i + 1 < cells.size) {
                    val cell2 = cells[i + 1].trim()
                    if (isDeliveryCandidate(cell2)) {
                        return DateDeliveryCandidate(cell1, cell2, i, i + 1)
                    }
                    if (cell2.isBlank() && i + 2 < cells.size) {
                        val cell3 = cells[i + 2].trim()
                        if (isDeliveryCandidate(cell3)) {
                            return DateDeliveryCandidate(cell1, cell3, i, i + 2)
                        }
                    }
                }
                return DateDeliveryCandidate(cell1, "", i, -1)
            }
        }
        
        // If no date found, try finding a delivery candidate
        for (i in startIndex until actualEndIndex) {
            val cell1 = cells[i].trim()
            if (isDeliveryCandidate(cell1)) {
                return DateDeliveryCandidate("", cell1, -1, i)
            }
        }
        
        return null
    }

    private fun isValidDate(text: String): Boolean {
        val match = dateRegex.find(text) ?: return false
        val month = match.groupValues[1].toIntOrNull() ?: return false
        val day = match.groupValues[2].toIntOrNull() ?: return false
        return month in 1..12 && day in 1..31
    }

    private fun isDeliveryFormat(text: String): Boolean {
        return text.matches(deliveryRegex) || text.matches(likelyDeliveryRegex) || text.matches(Regex("""^\d{1,4}\.0/\d{1,2}$"""))
    }

    private fun buildRow(dateText: String, deliveryText: String, location: String, noises: MutableList<IgnoredNoise>, side: String): LedgerRow? {
        val combined = "$dateText $deliveryText".trim()
        
        if (combined.contains("*") || combined.contains("x") || combined.contains("X") || combined.contains("×") || combined.contains("=") || combined.contains("合计") || combined.contains("小计") || combined.contains("0.9") || combined.contains("金额") || combined.contains("元")) {
            noises.add(IgnoredNoise(combined, "FORMULA (公式)", location))
            return null 
        }

        val isDateValid = isValidDate(dateText)
        val isDateLooksLikeDelivery = isDeliveryFormat(dateText) && !isDateValid
        val isDeliveryValid = isDeliveryFormat(deliveryText)
        
        if (dateText == "月日" || dateText == "日期" || deliveryText.contains("送出")) {
            noises.add(IgnoredNoise(combined, "HEADER_ROW", location))
            return null
        }
        
        if (isDateLooksLikeDelivery) {
            noises.add(IgnoredNoise(combined, "COLUMN_MISALIGNED (列错位)", location))
            val badRow = LedgerRow(dateText = null, deliveryRawText = null, sourceSide = side)
            badRow.status = RowStatus.ROW_MISALIGNED
            badRow.warnings += "列错位: 日期列内容为 $dateText"
            return badRow
        }
        
        // Is date a large isolated number? 
        if (dateText.matches(Regex("""^\d{3,5}$""")) && !isDateValid && !isDateLooksLikeDelivery) {
            noises.add(IgnoredNoise(dateText, "INVALID_DATE_NUMBER", location))
            if (isDeliveryValid) {
                // we have a missing date but valid delivery
                 val missingDateRow = LedgerRow(dateText = null, deliveryRawText = deliveryText, sourceSide = side)
                 missingDateRow.status = RowStatus.MISSING_DATE
                 return missingDateRow
            }
            return null
        }

        if (!isDateValid && !isDeliveryValid) {
            if (combined.isNotBlank() && combined.contains(Regex("""\d"""))) {
                 if (combined.contains("系统") || combined.contains("相机")) {
                     noises.add(IgnoredNoise(combined, "WATERMARK (水印)", location))
                 } else {
                     noises.add(IgnoredNoise(combined, "非日期或非法送出格式", location))
                 }
            }
            return null
        }

        val row = LedgerRow(
            dateText = dateText.takeIf { isDateValid },
            deliveryRawText = deliveryText.takeIf { isDeliveryValid },
            sourceSide = side
        )
        
        if (!isDateValid && isDeliveryValid) {
            row.status = RowStatus.MISSING_DATE
            row.warnings += "日期缺失或无效"
        }
        
        if (isDateValid && !isDeliveryValid && deliveryText.isNotBlank()) {
            // maybe signature or subtotal
            noises.add(IgnoredNoise(deliveryText, "INVALID_DELIVERY_PATTERN (非法送出格式)", location))
            row.status = RowStatus.MISSING_DELIVERY
            row.warnings += "送出数据无法识别 ($deliveryText)"
        }
        
        RowGroupCompletenessValidator.validate(row)
        
        val rawText = row.deliveryRawText
        if (rawText != null && row.status != RowStatus.MISSING_DATE && row.status != RowStatus.MISSING_DELIVERY) {
            DeliveryTextNormalizer.normalize(rawText, row)
        }
        
        return row
    }
}
