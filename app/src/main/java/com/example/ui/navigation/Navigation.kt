package com.example.ui.navigation

object Routes {
    const val HOME = "home"
    const val BATCH_TASKS = "batch_tasks"
    const val SETTINGS = "settings"
    const val CAMERA_GUIDANCE = "camera_guidance/{action}"
    const val HISTORY_BILLS = "history_bills"
    const val OCR_DEBUG = "ocr_debug/{taskId}"
    const val OCR_PREVIEW = "ocr_preview/{taskId}"
    const val PADDLE_TEST = "paddle_test"
    
    const val OCR_REVIEW = "ocr_review/{taskId}"
    
    fun createCameraGuidanceRoute(action: String) = "camera_guidance/$action"
    fun createOcrDebugRoute(taskId: Int) = "ocr_debug/$taskId"
    fun createOcrPreviewRoute(taskId: Int) = "ocr_preview/$taskId"
    fun createOcrReviewRoute(taskId: Int) = "ocr_review/$taskId"
}
