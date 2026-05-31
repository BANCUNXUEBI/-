package com.example.ocr.processing.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object LedgerImagePreprocessor {
    fun preprocess(originalFile: File, outputFile: File): File? {
        try {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options) ?: return null
            
            // Limit size to avoid OOM
            var workingBitmap = bitmap
            if (workingBitmap.width > 3000 || workingBitmap.height > 3000) {
                val ratio = minOf(3000f / workingBitmap.width, 3000f / workingBitmap.height)
                val w = (workingBitmap.width * ratio).toInt()
                val h = (workingBitmap.height * ratio).toInt()
                workingBitmap = Bitmap.createScaledBitmap(workingBitmap, w, h, true)
                if (workingBitmap != bitmap) {
                    bitmap.recycle()
                }
            }
            
            // 1. Grayscale and Contrast
            val bmpGrayscale = Bitmap.createBitmap(workingBitmap.width, workingBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmpGrayscale)
            val paint = Paint()
            
            val cm = ColorMatrix()
            cm.setSaturation(0f) // Grayscale
            
            // Contrast enhancement
            val scale = 1.25f
            val translate = -25f
            val cmContrast = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(cmContrast)
            
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(workingBitmap, 0f, 0f, paint)
            
            // Crop out bottom 5% (usually watermark) and top 5%
            val topCrop = (bmpGrayscale.height * 0.05).toInt()
            val cropHeight = (bmpGrayscale.height * 0.90).toInt()
            val cropped = Bitmap.createBitmap(bmpGrayscale, 0, topCrop, bmpGrayscale.width, cropHeight)
            
            val out = FileOutputStream(outputFile)
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
            
            workingBitmap.recycle()
            bmpGrayscale.recycle()
            cropped.recycle()
            
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
