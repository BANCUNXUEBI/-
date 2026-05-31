package com.example.ocr.processing

object RoiMerger {
    fun merge(mainPage: LedgerPage, roiPage: LedgerPage) {
        val roiDateMap = mutableMapOf<String, LedgerRow>()
        
        // Build a map of valid rows from ROI
        for (row in roiPage.rows) {
            if (row.dateText != null && (row.sets != null || row.boxes != null || row.deliveryRawText != null)) {
                roiDateMap[row.dateText] = row
            }
        }
        
        // Compare with main page
        for (i in mainPage.rows.indices) {
            val mainRow = mainPage.rows[i]
            val dateText = mainRow.dateText ?: continue
            val roiRow = roiDateMap[dateText] ?: continue
            
            // Check if delivery matches. We will compare normalized or raw texts if one is not normalized
            val mainVal = mainRow.normalizedDeliveryText ?: mainRow.deliveryRawText
            val roiVal = roiRow.normalizedDeliveryText ?: roiRow.deliveryRawText
            
            if (mainVal != roiVal && mainVal != null && roiVal != null) {
                // Determine if we need to flag a conflict
                // Both are parsed, but the values are different
                mainRow.status = RowStatus.POSSIBLE_OCR_VALUE_CONFLICT
                mainRow.warnings = mainRow.warnings + "主识别值($mainVal)与局部复核值($roiVal)不一致，请人工确认。"
                mainPage.confidenceReasons = mainPage.confidenceReasons + "日期 $dateText 送出列可能识别错误，主识别: $mainVal，局部识别: $roiVal"
                mainPage.confidenceScore -= 10
                
                // If main was perfectly valid, we demote the page
                if (mainPage.confidenceLevel == PageConfidenceLevel.HIGH) {
                    mainPage.confidenceLevel = PageConfidenceLevel.MEDIUM
                }
            } else if (mainVal == null && roiVal != null) {
                // If main missed it entirely, but ROI found it, we can adopt it (or we can just flag it constraint)
                // For now, let's adopt the ROI row
                mainRow.deliveryRawText = roiRow.deliveryRawText
                mainRow.normalizedDeliveryText = roiRow.normalizedDeliveryText
                mainRow.sets = roiRow.sets
                mainRow.boxes = roiRow.boxes
                mainRow.boxCapacity = roiRow.boxCapacity
                mainRow.status = roiRow.status
                mainRow.warnings = mainRow.warnings + "主识别漏提，采用了局部复核结果: $roiVal"
            }
        }
        
        mainPage.confidenceScore = mainPage.confidenceScore.coerceIn(0, 100)
    }
}
