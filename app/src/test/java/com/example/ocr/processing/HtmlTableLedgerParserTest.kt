package com.example.ocr.processing

import org.junit.Assert.*
import org.junit.Test

class HtmlTableLedgerParserTest {

    @Test
    fun testLeftAndRightSections_Fixed10Cols() {
        val html = """
            <table>
                <tr>
                    <td colspan="10">酒店名称：大唐饭店 地址：幸福路 电话：12345 送出：100</td>
                </tr>
                <tr>
                    <td>日/月</td><td>送出(套)</td><td>收回(套)</td><td>破损(套)</td><td>签字</td>
                    <td>日/月</td><td>送出(套)</td><td>收回(套)</td><td>破损(套)</td><td>签字</td>
                </tr>
                <tr>
                    <td>9.1</td><td>150/5</td><td></td><td></td><td>张三</td>
                    <td>9.30</td><td>60/2</td><td></td><td></td><td>王五</td>
                </tr>
                <tr>
                    <td>9.2</td><td>120/4</td><td></td><td></td><td></td>
                    <td></td><td></td><td></td><td></td><td></td>
                </tr>
            </table>
        """.trimIndent()

        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        assertEquals(3, page.rows.size)
        
        val r1 = page.rows[0]
        assertEquals("9.1", r1.dateText)
        assertEquals("150/5", r1.deliveryRawText)
        assertEquals("LEFT", r1.sourceSide)

        val r2 = page.rows[1]
        assertEquals("9.2", r2.dateText)
        assertEquals("120/4", r2.deliveryRawText)
        assertEquals("LEFT", r2.sourceSide)

        val r3 = page.rows[2]
        assertEquals("9.30", r3.dateText)
        assertEquals("60/2", r3.deliveryRawText)
        assertEquals("RIGHT", r3.sourceSide)
        
        assertEquals("大唐饭店", page.customerNameRaw)
    }

    @Test
    fun testRightSectionIncompleteCols() {
        val html = """
            <table>
                <tr>
                    <td>9.1</td><td>150/5</td><td></td><td></td><td></td>
                    <td>9.30</td><td>60/2</td>
                </tr>
            </table>
        """.trimIndent()

        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        assertEquals(2, page.rows.size)
        assertEquals("9.30", page.rows[1].dateText)
        assertEquals("RIGHT", page.rows[1].sourceSide)
    }

    @Test
    fun testZhongCanTangGuoNotCustomerName() {
        val html = """
            <table>
                <tr>
                    <td>中餐</td>
                </tr>
                <tr>
                    <td>汤锅</td>
                </tr>
                <tr>
                    <td>签字：</td>
                </tr>
                <tr>
                    <td>9.1</td><td>150/5</td><td></td><td></td><td></td>
                </tr>
            </table>
        """.trimIndent()

        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        assertNull(page.customerNameRaw)
        assertEquals(1, page.rows.size)
    }

    @Test
    fun testReturnAndDamageHandledAsNoise() {
        val html = """
            <table>
                <tr>
                    <td>9.1</td><td>150/5</td><td>5</td><td>2</td><td></td>
                    <td>9.30</td><td>60/2</td><td>10</td><td></td><td></td>
                </tr>
            </table>
        """.trimIndent()

        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        
        val r1 = page.rows[0]
        assertEquals("9.1", r1.dateText)
        assertEquals("150/5", r1.deliveryRawText)
        
        val r2 = page.rows[1]
        assertEquals("9.30", r2.dateText)
        assertEquals("60/2", r2.deliveryRawText)

        // Return and damage should be treated as noise
        val noises = page.ignoredNoises
        assertTrue(noises.any { it.rawText == "5" && it.reason == "RETURN_COLUMN" })
        assertTrue(noises.any { it.rawText == "2" && it.reason == "DAMAGE_COLUMN" })
        assertTrue(noises.any { it.rawText == "10" && it.reason == "RETURN_COLUMN" })
    }

    @Test
    fun testLeftSectionShifted() {
        val html = """
            <table>
                <tr>
                    <td></td><td>4.14</td><td>4.0/2</td><td>215</td><td></td><td>签字</td>
                </tr>
            </table>
        """.trimIndent()

        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        assertEquals(1, page.rows.size)
        
        val r1 = page.rows[0]
        assertEquals("4.14", r1.dateText)
        assertEquals("4.0/2", r1.deliveryRawText)
        assertEquals("LEFT", r1.sourceSide)
        assertEquals(40, r1.sets)
        assertEquals(2, r1.boxes)
        
        val noises = page.ignoredNoises
        assertTrue(noises.any { it.rawText == "215" && it.reason == "RETURN_COLUMN" })
    }

    @Test
    fun testRightSectionShifted() {
        val html = """
            <table>
                <tr>
                    <td></td><td></td><td></td><td></td><td></td><td></td><td>9.30</td><td>60/2</td><td>3</td><td></td>
                </tr>
            </table>
        """.trimIndent()

        val page = PaddleHtmlTableLedgerParser.parseHtmlTable(html)
        assertEquals(1, page.rows.size)
        
        val r1 = page.rows[0]
        assertEquals("9.30", r1.dateText)
        assertEquals("60/2", r1.deliveryRawText)
        assertEquals("RIGHT", r1.sourceSide)
        assertEquals(60, r1.sets)
        
        val noises = page.ignoredNoises
        assertTrue(noises.any { it.rawText == "3" && it.reason == "RETURN_COLUMN" })
    }
}
