package com.registry.app

import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Minimal .xlsx reader: extracts the first worksheet as rows of strings.
 * Handles both inline strings and the shared-strings table, since files
 * produced by Excel/Google Sheets/LibreOffice normally use shared strings
 * while our own XlsxWriter uses inline strings.
 */
object XlsxReader {

    fun read(input: InputStream): List<List<String>> {
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    parts[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        val sharedStrings = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        // Find the first sheet path via workbook rels; fall back to the common default.
        val sheetPath = parts.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") }
            ?: "xl/worksheets/sheet1.xml"
        val sheetBytes = parts[sheetPath] ?: return emptyList()

        return parseSheet(sheetBytes, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = newParser(bytes)
        var event = parser.eventType
        val sb = StringBuilder()
        var inSi = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        sb.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> if (inSi) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        strings.add(sb.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = newParser(bytes)
        var event = parser.eventType

        var currentRow = mutableListOf<String>()
        var currentCellType: String? = null
        var currentColIndex = -1
        val cellBuffer = mutableMapOf<Int, String>()
        var inValue = false
        val sb = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> cellBuffer.clear()
                        "c" -> {
                            currentCellType = parser.getAttributeValue(null, "t")
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            currentColIndex = refToColIndex(ref)
                        }
                        "v", "t" -> { inValue = true; sb.setLength(0) }
                    }
                }
                XmlPullParser.TEXT -> if (inValue) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> {
                            if (inValue && currentColIndex >= 0) {
                                val raw = sb.toString()
                                val resolved = if (currentCellType == "s") {
                                    raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                                } else raw
                                cellBuffer[currentColIndex] = resolved
                            }
                            inValue = false
                        }
                        "row" -> {
                            val maxCol = (cellBuffer.keys.maxOrNull() ?: -1)
                            currentRow = MutableList(maxCol + 1) { i -> cellBuffer[i] ?: "" }
                            rows.add(currentRow)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        return parser
    }

    /** Converts a cell reference like "C7" into a zero-based column index (2). */
    private fun refToColIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch.isLetter()) {
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else break
        }
        return col - 1
    }
}
