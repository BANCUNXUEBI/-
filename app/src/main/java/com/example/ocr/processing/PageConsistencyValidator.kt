package com.example.ocr.processing

object PageConsistencyValidator {
    /**
     * Checks page-level consistency like comparing the count of recognized dates 
     * versus the count of recognized delivery fields.
     */
    fun validate(page: LedgerPage) {
        val warnings = mutableListOf<String>()
        
        val dateCount = page.rows.count { !it.dateText.isNullOrBlank() && it.status != RowStatus.NOISE }
        val deliveryCount = page.rows.count { !it.deliveryRawText.isNullOrBlank() && it.status != RowStatus.NOISE }
        
        if (dateCount > deliveryCount) {
             warnings.add("警告: 识别到的日期数量(${dateCount}) 大于 送出数量(${deliveryCount})，说明可能漏识别送出列，请仔细核对。")
        } else if (deliveryCount > dateCount) {
             warnings.add("警告: 送出数量(${deliveryCount}) 大于 日期数量(${dateCount})，说明可能把收回列、破损列、签字栏或小计误识别成送出，请仔细核查。")
        }
        
        page.warnings = page.warnings + warnings
    }
}
