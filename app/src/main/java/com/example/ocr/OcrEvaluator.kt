package com.example.ocr

data class OcrEvaluationResult(
    val hasHotelName: Boolean,
    val dateCount: Int,
    val deliveryFormatCount: Int,
    val suspectedMissingSlashCount: Int,
    val hasWatermark: Boolean,
    val hasSubtotal: Boolean,
    val hasCrossTableRisk: Boolean, // e.g. a line containing multiple dates
    val markdownLength: Int,
    val jsonlLength: Int
)

object OcrEvaluator {
    private val hotelNameRegex = Regex("酒店名称|天大烤堂")
    private val dateRegex = Regex("(^|\\s|\\|)\\d{1,2}\\.\\d{1,2}(\\s|\\||$)")
    private val deliveryFormatRegex = Regex("\\d{1,3}/\\d{1,2}")
    private val missingSlashRegex = Regex("\\b(?:4012|6013|8014|9013|10015|12014|24018)\\b")
    private val watermarkRegex = Regex("vivo|ZEISS|\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}", RegexOption.IGNORE_CASE)
    private val subtotalRegex = Regex("\\b(小计|合计|\\d+\\s*x\\s*\\d+\\.\\d+|\\d+\\s*\\*\\s*\\d+\\.\\d+|[2-9]\\d{2,4})\\b") // Just basic heuristic

    fun evaluate(markdownText: String?, jsonlText: String?): OcrEvaluationResult {
        val md = markdownText ?: ""
        val jsonl = jsonlText ?: ""
        
        val lines = md.lines()
        
        var dateCount = 0
        var deliveryFormatCount = 0
        var suspectedMissingSlashCount = 0
        var hasCrossTableRisk = false
        
        for (line in lines) {
            val datesInLine = dateRegex.findAll(line).count()
            dateCount += datesInLine
            if (datesInLine >= 2) {
                hasCrossTableRisk = true
            }
            deliveryFormatCount += deliveryFormatRegex.findAll(line).count()
            suspectedMissingSlashCount += missingSlashRegex.findAll(line).count()
        }

        return OcrEvaluationResult(
            hasHotelName = hotelNameRegex.containsMatchIn(md),
            dateCount = dateCount,
            deliveryFormatCount = deliveryFormatCount,
            suspectedMissingSlashCount = suspectedMissingSlashCount,
            hasWatermark = watermarkRegex.containsMatchIn(md),
            hasSubtotal = subtotalRegex.containsMatchIn(md),
            hasCrossTableRisk = hasCrossTableRisk,
            markdownLength = md.length,
            jsonlLength = jsonl.length
        )
    }
}
