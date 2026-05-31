package com.example.ocr.processing

object RowGroupCompletenessValidator {
    /**
     * Checks whether a row has the mandatory 2 item parts: Date, Delivery.
     * 1. 都有 -> 完整记录 (由 normalizer 设置 COMPLETE)
     * 2. 有日期，但无送出 -> 漏识别 (MISSING_DELIVERY)
     * 3. 缺少日期，但有其他 -> NEED_MANUAL_REVIEW
     */
    fun validate(row: LedgerRow) {
        val hasDate = !row.dateText.isNullOrBlank()
        val hasDelivery = !row.deliveryRawText.isNullOrBlank()
        
        if (hasDate && !hasDelivery) {
            row.status = RowStatus.MISSING_DELIVERY
            row.warnings = row.warnings + "疑似漏识别送出套数"
            return
        }
        
        if (!hasDate && hasDelivery) {
            row.status = RowStatus.NEED_MANUAL_REVIEW
            row.warnings = row.warnings + "完整的送出记录缺少对应日期，请确认是否错位"
            return
        }
        
        if (!hasDelivery) {
            row.status = RowStatus.INVALID_FORMAT
            row.warnings = row.warnings + "缺少送出数据"
            return
        }
    }
}
