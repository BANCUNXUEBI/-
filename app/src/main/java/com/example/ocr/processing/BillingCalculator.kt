package com.example.ocr.processing

object BillingCalculator {
    const val DEFAULT_PRICE_PER_SET = 0.9

    data class BillingResult(
        val validTotalSets: Int,
        val shouldCalculateAmount: Double
    )

    /**
     * Calculates the final total amount based strictly on the valid row combinations.
     * Only lines that are COMPLETE, DELIVERY_AUTO_CORRECTED, DELIVERY_AUTO_RESTORED_SEPARATOR,
     * RARE_BOX_CAPACITY participate.
     */
    fun calculate(page: LedgerPage): BillingResult {
        var totalSets = 0
        
        for (row in page.rows) {
            val status = row.status
            if (status == RowStatus.COMPLETE || 
                status == RowStatus.DELIVERY_AUTO_CORRECTED || 
                status == RowStatus.DELIVERY_AUTO_RESTORED_SEPARATOR || 
                status == RowStatus.RARE_BOX_CAPACITY) {
                
                totalSets += row.sets ?: 0
            }
        }
        
        return BillingResult(
            validTotalSets = totalSets,
            shouldCalculateAmount = totalSets * DEFAULT_PRICE_PER_SET
        )
    }
}
