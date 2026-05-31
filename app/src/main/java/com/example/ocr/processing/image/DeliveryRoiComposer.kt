package com.example.ocr.processing.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object DeliveryRoiComposer {
    fun compose(preprocessedFile: File, outputFile: File, grid: GridInfo): File? {
        try {
            val bitmap = BitmapFactory.decodeFile(preprocessedFile.absolutePath) ?: return null
            
            // Col 0: Left Date
            // Col 1: Left Delivery
            val leftColStart = grid.columnXs[0]
            val leftColEnd = grid.columnXs[2]
            
            // Col 5: Right Date
            // Col 6: Right Delivery
            val rightColStart = grid.columnXs[5]
            val rightColEnd = grid.columnXs[7]
            
            val topY = grid.boundingBox[1]
            val bottomY = grid.boundingBox[3]
            var actualBottomY = minOf(bottomY, bitmap.height)
            if (actualBottomY <= topY) actualBottomY = bitmap.height
            val actualTopY = maxOf(0, topY)
            val h = actualBottomY - actualTopY
            
            val leftW = minOf(leftColEnd - leftColStart, bitmap.width - leftColStart)
            val rightW = minOf(rightColEnd - rightColStart, bitmap.width - rightColStart)
            
            if (leftW <= 0 || rightW <= 0 || h <= 0) return null
            
            val leftBmp = Bitmap.createBitmap(bitmap, leftColStart, actualTopY, leftW, h)
            val rightBmp = Bitmap.createBitmap(bitmap, rightColStart, actualTopY, rightW, h)
            
            // Stack them vertically. Left on top, right on bottom. Add some padding.
            val padding = 40
            val outW = maxOf(leftW, rightW) + padding * 2
            val outH = leftBmp.height + rightBmp.height + padding * 3
            
            val composed = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composed)
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // Optional: increase contrast again for the ROI to make it crisp for OCR
            val paint = Paint()
            val cm = ColorMatrix()
            val scale = 1.3f
            val translate = -30f
            cm.set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
            
            canvas.drawBitmap(leftBmp, padding.toFloat(), padding.toFloat(), paint)
            canvas.drawBitmap(rightBmp, padding.toFloat(), (padding * 2 + leftBmp.height).toFloat(), paint)
            
            val out = FileOutputStream(outputFile)
            composed.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()
            
            bitmap.recycle()
            leftBmp.recycle()
            rightBmp.recycle()
            composed.recycle()
            
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
