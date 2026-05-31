package com.example.ocr.processing

import org.jsoup.nodes.Document

object HtmlTableToGrid {
    fun parse(document: Document): List<List<String>> {
        val trs = document.select("tr")
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        var maxCol = 0

        for (r in trs.indices) {
            val tds = trs[r].select("td, th")
            var c = 0
            for (td in tds) {
                while (grid[r]?.get(c) != null) {
                    c++
                }
                
                val rowspan = td.attr("rowspan").toIntOrNull() ?: 1
                val colspan = td.attr("colspan").toIntOrNull() ?: 1
                val text = td.text().replace(Regex("\\s+"), "")

                for (rr in r until r + rowspan) {
                    val rowMap = grid.getOrPut(rr) { mutableMapOf() }
                    for (cc in c until c + colspan) {
                        rowMap[cc] = text
                        if (cc > maxCol) maxCol = cc
                    }
                }
                c += colspan
            }
        }

        val result = mutableListOf<List<String>>()
        for (r in 0 until (grid.keys.maxOrNull()?.plus(1) ?: 0)) {
            val row = mutableListOf<String>()
            for (c in 0..maxCol) {
                row.add(grid[r]?.get(c) ?: "")
            }
            result.add(row)
        }
        return result
    }
}
