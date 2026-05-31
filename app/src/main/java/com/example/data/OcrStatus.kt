package com.example.data

enum class OcrStatus(val label: String) {
    WAITING("排队中"),
    UPLOADING("上传中"),
    JOB_SUBMITTED("正在识别"),
    POLLING("正在识别"),
    OCR_DONE("正在计算"),
    JSONL_DOWNLOADING("正在计算"),
    JSONL_DOWNLOADED("正在计算"),
    TABLE_EXTRACTING("正在计算"),
    LEDGER_PARSING("正在计算"),
    PARSED_PREVIEW_READY("正在计算"),
    NEED_REVIEW("有疑问"),
    HUMAN_CONFIRMED("已确认"),
    LOCKED_FOR_BILLING("已锁定"),
    PROCESSING("正在计算"),
    OCR_PROCESSING("正在识别"),
    COMPLETED("待确认"),
    FAILED("识别失败"),
    DUPLICATE_SUSPECTED("疑似重复")
}
