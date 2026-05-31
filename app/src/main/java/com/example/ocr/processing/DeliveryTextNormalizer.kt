package com.example.ocr.processing

object DeliveryTextNormalizer {
    /**
     * Resolves strings like "12014" -> "120/4"
     */
    fun restoreSeparator(rawString: String): String? {
        val str = rawString.trim()
        if (str.length >= 3 && str.all { it.isDigit() }) {
            // Usually the last digit is the box count, or last two digits.
            for (i in 1..2) {
                if (str.length <= i) continue
                val separatorIndex = str.length - i
                val setsStr = str.substring(0, separatorIndex)
                val boxesStr = str.substring(separatorIndex)
                
                val sets = setsStr.toIntOrNull()
                val boxes = boxesStr.toIntOrNull()
                
                if (sets != null && boxes != null && boxes > 0) {
                    val validation = BoxRuleValidator.validate(sets, boxes)
                    if (validation.isValid || validation.suggestedSets != null) {
                        return "$sets/$boxes"
                    }
                }
            }
        }
        return str
    }

    /**
     * Determines the sets and boxes, applying corrections if needed.
     */
    fun normalize(rawText: String, row: LedgerRow) {
        var preprocessed = rawText.trim()
        var customWarning: String? = null
        var isAutoCorrected = false
        var isSeparatorRestored = false

        // Rule 1: X.0/Y -> X0/Y
        val dotMismatchRegex = Regex("""^(\d{1,4})\.0/(\d{1,2})$""")
        val dotMatch = dotMismatchRegex.find(preprocessed)
        if (dotMatch != null) {
            val setsStr = dotMatch.groupValues[1] + "0"
            val boxesStr = dotMatch.groupValues[2]
            preprocessed = "$setsStr/$boxesStr"
            isAutoCorrected = true
            customWarning = "OCR疑似将 0 识别为小数点或错位，已从 $rawText 修正为 $preprocessed。"
        } else {
            // Rule 2: X6/Y -> X0/Y
            // Matches two or three digit sets ending in 6
            val sixRegex = Regex("""^(\d{1,3})6/(\d{1,2})$""")
            val sixMatch = sixRegex.find(preprocessed)
            if (sixMatch != null) {
                val prefix = sixMatch.groupValues[1]
                val boxesStr = sixMatch.groupValues[2]
                preprocessed = "${prefix}0/$boxesStr"
                isAutoCorrected = true
                customWarning = "OCR疑似将 0 识别为 6，已从 $rawText 修正为 $preprocessed。"
            } else if (!preprocessed.contains("/")) {
                // Rule 3: Missing separator (e.g., 12014)
                val restored = restoreSeparator(preprocessed)
                if (restored != null && restored != preprocessed) {
                    preprocessed = restored
                    isSeparatorRestored = true
                    customWarning = "系统已自动恢复分隔符：$rawText -> $preprocessed"
                }
            }
        }

        val parts = preprocessed.split("/")
        if (parts.size == 2) {
            val sets = parts[0].trim().toIntOrNull()
            val boxes = parts[1].trim().toIntOrNull()
            
            if (sets != null && boxes != null) {
                val validation = BoxRuleValidator.validate(sets, boxes)
                if (validation.isValid) {
                    row.sets = sets
                    row.boxes = boxes
                    row.boxCapacity = sets / boxes
                    row.normalizedDeliveryText = preprocessed
                    
                    if (isAutoCorrected) {
                        row.status = RowStatus.NORMALIZED_COMPLETE
                        row.warnings = row.warnings + customWarning!!
                    } else if (isSeparatorRestored) {
                        row.status = RowStatus.NORMALIZED_COMPLETE
                        row.warnings = row.warnings + customWarning!!
                    } else if (validation.isRare) {
                        row.status = RowStatus.RARE_BOX_CAPACITY
                        row.warnings = row.warnings + validation.warning!!
                    } else {
                        row.status = RowStatus.COMPLETE
                    }
                } else if (validation.suggestedSets != null && !isAutoCorrected && !isSeparatorRestored) {
                    row.status = RowStatus.DELIVERY_AUTO_CORRECTED
                    row.sets = validation.suggestedSets
                    row.boxes = boxes
                    row.boxCapacity = validation.suggestedSets / boxes
                    row.normalizedDeliveryText = "${validation.suggestedSets}/$boxes"
                    row.warnings = row.warnings + "OCR原始识别为$preprocessed，系统根据箱规校正为 ${validation.suggestedSets}/$boxes，请核对。"
                } else {
                    row.status = RowStatus.NEED_MANUAL_REVIEW
                    if (isAutoCorrected || isSeparatorRestored) {
                         row.warnings = row.warnings + (customWarning!! + " 且箱规校验未通过(${sets}/$boxes): ${validation.warning}")
                    } else {
                         row.warnings = row.warnings + validation.warning!!
                    }
                }
                return
            }
        }
        
        row.status = RowStatus.INVALID_FORMAT
        row.warnings = row.warnings + "无法解析的送出格式: $rawText"
    }
}

