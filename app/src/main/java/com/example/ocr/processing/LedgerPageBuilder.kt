package com.example.ocr.processing

import com.example.data.OcrTask

object LedgerPageBuilder {
    fun buildFromTask(task: OcrTask): LedgerPage? {
        if (task.jsonlText.isNullOrBlank()) {
            return null
        }
        
        val page = PaddleHtmlTableLedgerParser.parseJsonl(task.jsonlText)
        
        if (!task.deliveryRoiJsonlText.isNullOrBlank()) {
            val roiPage = PaddleHtmlTableLedgerParser.parseJsonl(task.deliveryRoiJsonlText)
            RoiMerger.merge(page, roiPage)
        }
        
        if (task.preprocessedImageUri != null) {
            LedgerCellCropExtractor.extractForPage(page, task)
        }
        
        val totalSets = page.rows.mapNotNull { 
            if (it.status == RowStatus.COMPLETE || 
                it.status == RowStatus.NORMALIZED_COMPLETE || 
                it.status == RowStatus.RARE_BOX_CAPACITY || 
                it.status == RowStatus.DELIVERY_AUTO_CORRECTED || 
                it.status == RowStatus.DELIVERY_AUTO_RESTORED_SEPARATOR) {
                it.sets 
            } else null 
        }.sum()
        
        val crossCheckWarning = SubtotalCrossChecker.check(page, totalSets)
        if (crossCheckWarning != null) {
            page.warnings = page.warnings + crossCheckWarning
            page.confidenceScore -= 15
            if (page.confidenceLevel == PageConfidenceLevel.HIGH) {
                page.confidenceLevel = PageConfidenceLevel.MEDIUM
            }
        }
        
        // Re-evaluate page confidence taking into account possible new noise/value conflicts
        PageConfidenceScorer.evaluate(page)
        
        return page
    }
}
