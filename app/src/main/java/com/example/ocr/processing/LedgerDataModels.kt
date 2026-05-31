package com.example.ocr.processing

enum class RowStatus {
    COMPLETE,
    NORMALIZED_COMPLETE,
    DELIVERY_AUTO_CORRECTED,
    DELIVERY_AUTO_RESTORED_SEPARATOR,
    RARE_BOX_CAPACITY,
    MISSING_DELIVERY,
    MISSING_DATE,
    INVALID_FORMAT,
    ROW_MISALIGNED,
    NEED_MANUAL_REVIEW,
    DUPLICATE_SUSPECTED,
    VALID_BUT_POSSIBLY_WRONG_BY_OCR,
    POSSIBLE_OCR_VALUE_CONFLICT,
    MANUAL_CONFIRMED,
    FAILED,
    NOISE // for skipped rows like signature/subtotal
}

enum class CustomerMatchStatus {
    MATCHED_EXACT,
    MATCHED_FUZZY,
    MATCHED_ALIAS,
    UNMATCHED_HIGH_CONFIDENCE,
    UNMATCHED_LOW_CONFIDENCE,
    NOT_FOUND
}

data class LedgerRow(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dateText: String?,
    var deliveryRawText: String?,
    var normalizedDeliveryText: String? = null,
    var sets: Int? = null,
    var boxes: Int? = null,
    var boxCapacity: Int? = null,
    var status: RowStatus = RowStatus.NEED_MANUAL_REVIEW,
    var warnings: List<String> = emptyList(),
    val sourceSide: String = "LEFT", // LEFT or RIGHT
    var dateCellCropPath: String? = null,
    var deliveryCellCropPath: String? = null,
    var rowCropPath: String? = null
)

data class IgnoredNoise(
    val rawText: String,
    val reason: String,
    val sourceLocation: String? = null
)

data class LedgerPage(
    val customerNameRaw: String?,
    var customerMatchStatus: CustomerMatchStatus = CustomerMatchStatus.NOT_FOUND,
    var rows: List<LedgerRow> = emptyList(),
    var warnings: List<String> = emptyList(),
    var parsedTableCount: Int = 0,
    var ignoredNoises: List<IgnoredNoise> = emptyList(),
    var confidenceScore: Int = 100,
    var confidenceLevel: PageConfidenceLevel = PageConfidenceLevel.HIGH,
    var confidenceReasons: List<String> = emptyList()
)

enum class PageConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}
