package com.example.ocr.processing

object PageConfidenceScorer {
    fun evaluate(page: LedgerPage) {
        var score = 100
        val reasons = mutableListOf<String>()
        
        val validRows = page.rows.filter { 
            it.status == RowStatus.COMPLETE || 
            it.status == RowStatus.NORMALIZED_COMPLETE || 
            it.status == RowStatus.DELIVERY_AUTO_CORRECTED || 
            it.status == RowStatus.DELIVERY_AUTO_RESTORED_SEPARATOR || 
            it.status == RowStatus.RARE_BOX_CAPACITY 
        }
        
        if (validRows.isEmpty()) {
            score -= 80
            reasons.add("未识别到任何有效记录")
        } else if (validRows.size < 5) {
            score -= 30
            reasons.add("有效记录数较少 (${validRows.size}条)")
        }
        
        val leftRows = validRows.filter { it.sourceSide == "LEFT" }
        val rightRows = validRows.filter { it.sourceSide == "RIGHT" }
        
        if (leftRows.size > 10 && rightRows.isEmpty()) {
            score -= 40
            reasons.add("左侧记录较多但右侧无任何记录，疑似右侧漏识别")
        }
        
        if (page.ignoredNoises.size > validRows.size * 2 && validRows.isNotEmpty()) {
            score -= 20
            reasons.add("异常噪点过多")
        }
        
        val misalignedRows = page.rows.filter { 
            it.status == RowStatus.ROW_MISALIGNED || 
            it.status == RowStatus.INVALID_FORMAT || 
            it.status == RowStatus.NEED_MANUAL_REVIEW ||
            it.status == RowStatus.MISSING_DELIVERY ||
            it.status == RowStatus.MISSING_DATE ||
            it.status == RowStatus.VALID_BUT_POSSIBLY_WRONG_BY_OCR ||
            it.status == RowStatus.POSSIBLE_OCR_VALUE_CONFLICT
        }
        if (misalignedRows.size > 3) {
            score -= 30
            reasons.add("格式异常或需要人工复核的行数过多 (${misalignedRows.size}条)")
        }
        
        val level = when {
            score >= 80 -> PageConfidenceLevel.HIGH
            score >= 50 -> PageConfidenceLevel.MEDIUM
            else -> PageConfidenceLevel.LOW
        }
        
        page.confidenceScore = score.coerceIn(0, 100)
        page.confidenceLevel = level
        page.confidenceReasons = reasons
    }
}
