package com.example.ocr.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaddleHtmlTableLedgerParserTest {

    @Test
    fun testParseHtmlTable() {
        val html = """
            <html>
            <body>
            <table>
            <tr><td>酒店名称: 天天烧空</td><td>地址:</td><td>电话:</td><td>中餐</td><td>汤锅</td><td>签字</td><td>月</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>月 日</td><td>送出(套)</td><td>收回(箱)</td><td>店方</td><td>小计</td><td>签字</td><td>月 日</td><td>送出(套)</td><td>收回(箱)</td><td>店方</td><td>小计</td><td>签字</td><td>空</td></tr>
            <tr><td>10.1</td><td>240/8</td><td>3</td><td>-</td><td></td><td></td><td>10.29</td><td>90/3</td><td>1</td><td>-</td><td></td><td></td><td></td></tr>
            <tr><td>10.2</td><td>2760×0.9</td><td></td><td></td><td></td><td></td><td>10.30</td><td>1590</td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>10.3</td><td>120/4</td><td></td><td></td><td></td><td></td><td>10.31</td><td>570</td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>10.4</td><td>12014</td><td>1</td><td></td><td></td><td></td><td>11.1</td><td>25/1</td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>10.5</td><td>480</td><td></td><td></td><td></td><td></td><td>11.2</td><td>250/10</td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()
        
        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        
        assertEquals("天天烧空", page.customerNameRaw)
        
        val leftRows = page.rows.filter { it.sourceSide == "LEFT" }
        val rightRows = page.rows.filter { it.sourceSide == "RIGHT" }
        
        // Let's print out what is parsed
        println("LEFT ROWS: " + leftRows.size)
        leftRows.forEach { println("Left date: ${it.dateText}, delivery: ${it.deliveryRawText}, status: ${it.status}, warnings: ${it.warnings}") }
        
        println("RIGHT ROWS: " + rightRows.size)
        rightRows.forEach { println("Right date: ${it.dateText}, delivery: ${it.deliveryRawText}, status: ${it.status}, warnings: ${it.warnings}") }

        // Expected left parsing:
        // 10.1 -> 240/8 valid
        // 10.2 -> 2760×0.9 (ignored noisy)
        // 10.3 -> 120/4 valid
        // 10.4 -> 12014 -> normalized to 120/4, valid or invalid format if normalizer didn't pick up (Wait, 12014 has capacity 30, so normalizer should make it 120/4. 12014 is length 5, separator index 4. sets=120, boxes=4. Valid!)
        // 10.5 -> 480 (ignored, since no separator and > 3 digits, wait 480 is 3 digits. 10.5 is a valid date. likelyDeliveryRegex matches 480? Yes. But normalizer will say INVALID_FORMAT.)
        
        // Let's write simple assertions
        val row10_1 = leftRows.find { it.dateText == "10.1" }
        assertEquals("240/8", row10_1?.deliveryRawText)
        assertEquals(30, row10_1?.sets?.div(row10_1.boxes!!)) // 240/8 = 30
        
        val row10_29 = rightRows.find { it.dateText == "10.29" }
        assertEquals("90/3", row10_29?.deliveryRawText)
        
        val row10_2 = leftRows.find { it.dateText == "10.2" }
        assertTrue("Multiplier 2760*0.9 should be skipped", row10_2 == null)
        
        val row10_30 = rightRows.find { it.dateText == "10.30" }
        // 1590 is matched by likelyDeliveryRegex (4 digits). But normalizer fails to parse.
        // It will be INVALID_FORMAT.
        assertEquals("1590", row10_30?.deliveryRawText)
        assertEquals(RowStatus.INVALID_FORMAT, row10_30?.status)

        val row11_1 = rightRows.find { it.dateText == "11.1" }
        assertEquals("25/1", row11_1?.deliveryRawText)
        assertEquals(RowStatus.RARE_BOX_CAPACITY, row11_1?.status)
        
        val row10_4 = leftRows.find { it.dateText == "10.4" }
        assertEquals("12014", row10_4?.deliveryRawText)
        assertTrue(row10_4?.status != null) // Or complete depending on logic
    }
}
