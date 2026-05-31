package com.example.ocr.processing
import kotlin.math.abs

object BoxRuleValidator {
    val VALID_CAPACITIES = listOf(20, 25, 30, 40)
    
    data class BoxValidationResult(
        val isValid: Boolean,
        val isRare: Boolean,
        val suggestedSets: Int?,
        val warning: String?
    )

    fun validate(sets: Int, boxes: Int): BoxValidationResult {
        if (boxes <= 0) return BoxValidationResult(false, false, null, "Box count must be > 0")
        
        val capacity = sets / boxes.toFloat()
        
        if (capacity == 20f || capacity == 30f || capacity == 40f) {
            return BoxValidationResult(isValid = true, isRare = false, suggestedSets = null, warning = null)
        }
        
        if (capacity == 25f) {
            return BoxValidationResult(
                isValid = true, 
                isRare = true, 
                suggestedSets = null, 
                warning = "稀有箱型: 25套/箱"
            )
        }

        // Handle closest capacity correction if invalid
        var closestCapacity = -1
        var minDiff = Int.MAX_VALUE
        
        for (validCap in VALID_CAPACITIES) {
            val validSets = validCap * boxes
            val diff = abs(validSets - sets)
            if (diff < minDiff) {
                minDiff = diff
                closestCapacity = validCap
            }
        }
        
        val suggestedSets = closestCapacity * boxes
        
        // 允许偏差不宜过大，例如超过单箱的 30% 就不应该强行校正
        val maxAllowedDiff = closestCapacity * 0.3
        if (minDiff <= maxAllowedDiff) {
             return BoxValidationResult(
                 isValid = false,
                 isRare = false,
                 suggestedSets = suggestedSets,
                 warning = "容量偏差可校正。原值 ${sets}/$boxes，建议 ${suggestedSets}/$boxes"
             )
        }

        return BoxValidationResult(
            isValid = false,
            isRare = false, 
            suggestedSets = null,
            warning = "偏差过大，请人工复核"
        )
    }
}
