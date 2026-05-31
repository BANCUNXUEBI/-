package com.example.ocr.processing

import org.junit.Assert.*
import org.junit.Test

class DeliveryTextNormalizerTest {

    @Test
    fun testCorrection6To0_Valid() {
        val row = LedgerRow(dateText = "9.1", deliveryRawText = "96/3")
        DeliveryTextNormalizer.normalize("96/3", row)
        
        assertEquals(RowStatus.NORMALIZED_COMPLETE, row.status)
        assertEquals(90, row.sets)
        assertEquals(3, row.boxes)
        assertEquals(30, row.boxCapacity)
        assertEquals("90/3", row.normalizedDeliveryText)
        assertTrue(row.warnings.any { it.contains("OCR疑似将 0 识别为 6") })
    }

    @Test
    fun testCorrection6To0_66_2_Valid() {
        val row = LedgerRow(dateText = "9.2", deliveryRawText = "66/2")
        DeliveryTextNormalizer.normalize("66/2", row)
        
        assertEquals(RowStatus.NORMALIZED_COMPLETE, row.status)
        assertEquals(60, row.sets)
        assertEquals(2, row.boxes)
        assertEquals(30, row.boxCapacity)
        assertEquals("60/2", row.normalizedDeliveryText)
    }

    @Test
    fun testCorrection6To0_126_4_Valid() {
        val row = LedgerRow(dateText = "9.3", deliveryRawText = "126/4")
        DeliveryTextNormalizer.normalize("126/4", row)
        
        assertEquals(RowStatus.NORMALIZED_COMPLETE, row.status)
        assertEquals(120, row.sets)
        assertEquals(4, row.boxes)
        assertEquals(30, row.boxCapacity)
    }

    @Test
    fun testCorrectionDotMismatch_4_0_2() {
        val row = LedgerRow(dateText = "9.4", deliveryRawText = "4.0/2")
        DeliveryTextNormalizer.normalize("4.0/2", row)
        
        assertEquals(RowStatus.NORMALIZED_COMPLETE, row.status)
        assertEquals(40, row.sets)
        assertEquals(2, row.boxes)
        assertEquals("40/2", row.normalizedDeliveryText)
        assertTrue(row.warnings.any { it.contains("OCR疑似将 0 识别为小数点") })
    }

    @Test
    fun testCorrectionDotMismatch_10_0_5() {
        val row = LedgerRow(dateText = "9.5", deliveryRawText = "10.0/5")
        DeliveryTextNormalizer.normalize("10.0/5", row)
        
        // 100/5 -> could be 20 capacity, which is valid
        assertEquals(100, row.sets)
        assertEquals(5, row.boxes)
        assertEquals(RowStatus.NORMALIZED_COMPLETE, row.status)
    }

    @Test
    fun testCorrection6To0_Invalid() {
        val row = LedgerRow(dateText = "9.6", deliveryRawText = "76/3")
        DeliveryTextNormalizer.normalize("76/3", row)
        
        // 70/3 is invalid capacity (23.33) -> should fall to NEED_MANUAL_REVIEW
        assertEquals(RowStatus.NEED_MANUAL_REVIEW, row.status)
        assertNull(row.sets)
        assertTrue(row.warnings.any { it.contains("校验未通过") })
    }
}
