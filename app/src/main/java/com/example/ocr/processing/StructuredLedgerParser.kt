package com.example.ocr.processing

object StructuredLedgerParser {
    
    /**
     * The master entry point that ties parser + rules together
     */
    fun processRawOcr(markdownText: String?, jsonlText: String?): LedgerPage {
        if (!jsonlText.isNullOrBlank()) {
             return PaddleHtmlTableLedgerParser.parseJsonl(jsonlText)
        }
        
        return LedgerPage(customerNameRaw = null)
    }
}
