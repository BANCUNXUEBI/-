package com.example.ocr.processing

import com.example.data.OcrTask

object LedgerCellCropExtractor {
    fun extractForPage(page: LedgerPage, task: OcrTask) {
        // Since we don't have exact coordinate bounds from HTML parser right now,
        // we can set the row crops or delivery crops to the ROI image as a fallback.
        // Once we have a polygon-to-HTML mapping, we can crop the exact cells here.
        val defaultCrop = task.deliveryRoiImageUri ?: task.preprocessedImageUri
        
        for (row in page.rows) {
            row.deliveryCellCropPath = defaultCrop
            row.rowCropPath = task.preprocessedImageUri
        }
    }
}
