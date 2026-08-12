package com.registry.app

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bridges the web app's export/import/WhatsApp buttons to native Android
 * behavior. Called from JS via window.AndroidFile.*
 */
class FileBridge(private val activity: MainActivity) {

    /**
     * Builds the .xlsx from JSON (headers + rows, all strings) and asks the
     * user where to save it via the system file picker (SAF).
     * JS calls this, then Android shows the picker; there is no return value
     * here because the picker result comes back asynchronously.
     */
    @JavascriptInterface
    fun exportToPickedLocation(headersJson: String, rowsJson: String) {
        val headers = jsonArrayToStrings(headersJson)
        val rows = jsonArray2DToStrings(rowsJson)
        activity.startExportFlow(headers, rows, forWhatsApp = false)
    }

    /**
     * Same export, but writes to app-private storage and immediately opens
     * WhatsApp's share sheet addressed to the fixed number, file attached.
     * The final "Send" tap inside WhatsApp is still required by WhatsApp
     * itself — apps cannot send messages on a user's behalf silently.
     */
    @JavascriptInterface
    fun exportAndShareToWhatsApp(headersJson: String, rowsJson: String, phoneNumber: String) {
        val headers = jsonArrayToStrings(headersJson)
        val rows = jsonArray2DToStrings(rowsJson)
        activity.startWhatsAppExportFlow(headers, rows, phoneNumber)
    }

    /** Opens the system file picker so the user can choose an .xlsx to import. */
    @JavascriptInterface
    fun pickImportFile() {
        activity.startImportFlow()
    }

    companion object {
        fun timestampedFileName(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
            return "registry-${fmt.format(Date())}.xlsx"
        }

        fun jsonArrayToStrings(json: String): List<String> {
            val arr = JSONArray(json)
            return List(arr.length()) { i -> arr.optString(i, "") }
        }

        fun jsonArray2DToStrings(json: String): List<List<String>> {
            val arr = JSONArray(json)
            return List(arr.length()) { i ->
                val row = arr.getJSONArray(i)
                List(row.length()) { j -> row.optString(j, "") }
            }
        }

        /** Result of a parsed import file, sent back to JS as JSON. */
        fun importResultJson(headers: List<String>, rows: List<List<String>>): String {
            val obj = JSONObject()
            obj.put("headers", JSONArray(headers))
            val rowsArr = JSONArray()
            rows.forEach { row -> rowsArr.put(JSONArray(row)) }
            obj.put("rows", rowsArr)
            return obj.toString()
        }
    }
}

/** Small helper for building an exports directory inside app-private cache. */
fun exportsDir(cacheDir: File): File {
    val dir = File(cacheDir, "exports")
    if (!dir.exists()) dir.mkdirs()
    return dir
}
