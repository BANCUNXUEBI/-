package com.example.ocr.processing

object SubtotalCrossChecker {
    /**
     * Extracts total sets from noises (subtotal / formula) and compares them with the calculated sum.
     * Returns a warning string if there is a realistic discrepancy (e.g., 20, 30, 40, etc.).
     */
    fun check(page: LedgerPage, calculatedTotalSets: Int): String? {
        var apparentTotalSets: Int? = null
        
        for (noise in page.ignoredNoises) {
            if (noise.reason == "FORMULA (公式)" || noise.reason == "SUBTOTAL") {
                // e.g. 1020 * 0.9 = 918, or 2160 × 0.9 = 1944
                val regex = Regex("""(\d{3,5})\s*[\*xX×]\s*0\.9""")
                val match = regex.find(noise.rawText)
                if (match != null) {
                    apparentTotalSets = match.groupValues[1].toIntOrNull()
                    break
                }
            }
        }
        
        if (apparentTotalSets != null && calculatedTotalSets > 0) {
            val diff = Math.abs(apparentTotalSets - calculatedTotalSets)
            // If the discrepancy is a typical delivery value or box value
            // such as 20, 30, 40, 60, 90, 120...
            val typicalDiffs = listOf(20, 25, 30, 40, 50, 60, 75, 80, 90, 100, 120, 125, 150, 160)
            if (diff > 0 && diff in typicalDiffs) {
                return "系统试算(${calculatedTotalSets}套)与手写小计(${apparentTotalSets}套)相差 ${diff} 套，疑似某行送出被误识别，请人工核对。"
            } else if (diff > 0) {
                return "系统试算(${calculatedTotalSets}套)与手写小计(${apparentTotalSets}套)相差 ${diff} 套，请核对。"
            }
        }
        
        return null
    }
}
