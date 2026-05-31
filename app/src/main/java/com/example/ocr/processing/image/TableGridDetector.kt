package com.example.ocr.processing.image

import android.graphics.Bitmap

object TableGridDetector {
    fun detect(bitmap: Bitmap): GridInfo {
        // Fallback to 10 columns
        val w = bitmap.width
        val h = bitmap.height
        // Header ~ top 15%, Watermark ~ bottom 5%
        val topY = (h * 0.15).toInt()
        val bottomY = (h * 0.95).toInt()
        
        val cols = mutableListOf<Int>()
        for (i in 0..10) {
            cols.add(i * w / 10)
        }
        
        return GridInfo(
            boundingBox = listOf(0, topY, w, bottomY),
            columnXs = cols,
            rowYs = listOf(topY, bottomY)
        )
    }
}

data class GridInfo(
    val boundingBox: List<Int>, // Left, Top, Right, Bottom
    val columnXs: List<Int>,
    val rowYs: List<Int>
)
