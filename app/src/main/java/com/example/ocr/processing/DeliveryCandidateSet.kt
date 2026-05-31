package com.example.ocr.processing

object DeliveryCandidateSet {
    // Generate valid combinations based on valid box capacities: 20, 25, 30, 40
    val validCapacities = listOf(20, 25, 30, 40)
    
    // Some common sets/boxes candidates
    val candidates: Set<String> by lazy {
        val set = mutableSetOf<String>()
        // Generate common combinations from 1 to 10 boxes
        for (capacity in validCapacities) {
            for (boxes in 1..10) {
                val sets = boxes * capacity
                set.add("$sets/$boxes")
            }
        }
        
        // Add a few known missing or rare combinations if any
        set.add("150/5") // 30 capacity is standard, 5*30=150. Already in logic above.
        
        set
    }

    fun isCandidate(text: String): Boolean {
        return text in candidates
    }
}
