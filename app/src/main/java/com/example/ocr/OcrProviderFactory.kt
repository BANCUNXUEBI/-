package com.example.ocr

import com.example.ocr.paddle.PaddleOcrProvider

object OcrProviderFactory {
    private val mockProvider = MockOcrProvider()
    private val paddleProvider = PaddleOcrProvider()

    fun getProvider(type: OcrProviderType): OcrProvider {
        return when (type) {
            OcrProviderType.MOCK -> mockProvider
            OcrProviderType.PADDLE_OCR -> paddleProvider
        }
    }
}
