package com.registry.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Held between "JS asked to export" and "user picked a save location".
    private var pendingExportHeaders: List<String> = emptyList()
    private var pendingExportRows: List<List<String>> = emptyList()

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            XlsxWriter.write(out, "الأشخاص", pendingExportHeaders, pendingExportRows)
                        }
                        Toast.makeText(this, "تم حفظ ملف الإكسل", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "فشل حفظ الملف: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        val rows = contentResolver.openInputStream(uri)?.use { input ->
                            XlsxReader.read(input)
                        } ?: emptyList()
                        if (rows.isEmpty()) {
                            Toast.makeText(this, "الملف فارغ أو غير مدعوم", Toast.LENGTH_LONG).show()
                            return@registerForActivityResult
                        }
                        val headers = rows[0]
                        val dataRows = rows.drop(1)
                        val json = FileBridge.importResultJson(headers, dataRows)
                        val escaped = JSONArray().put(json).getString(0)
                        webView.evaluateJavascript(
                            "window.onAndroidImportResult && window.onAndroidImportResult(${orgJsonStringLiteral(escaped)});",
                            null
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this, "تعذّرت قراءة الملف: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(SqliteBridge(this), "AndroidDB")
        webView.addJavascriptInterface(FileBridge(this), "AndroidFile")

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    // ---------- Export flow (SAF "create document") ----------

    fun startExportFlow(headers: List<String>, rows: List<List<String>>, forWhatsApp: Boolean) {
        pendingExportHeaders = headers
        pendingExportRows = rows
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_TITLE, FileBridge.timestampedFileName())
        }
        createDocumentLauncher.launch(intent)
    }

    // ---------- WhatsApp export + share flow ----------

    fun startWhatsAppExportFlow(headers: List<String>, rows: List<List<String>>, phoneNumber: String) {
        try {
            val dir = exportsDir(cacheDir)
            val file = File(dir, FileBridge.timestampedFileName())
            FileOutputStream(file).use { out ->
                XlsxWriter.write(out, "الأشخاص", headers, rows)
            }
            val uri: Uri = FileProvider.getUriForFile(this, "com.registry.app.fileprovider", file)

            val cleanNumber = phoneNumber.filter { it.isDigit() }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra("jid", "$cleanNumber@s.whatsapp.net")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(sendIntent)
            } catch (e: Exception) {
                // WhatsApp not installed, or the jid-targeted intent isn't accepted on this
                // device/build — fall back to a generic share sheet with the file attached.
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(fallback, "مشاركة ملف الإكسل"))
                Toast.makeText(
                    this,
                    "لم يتم فتح واتساب مباشرة على الرقم — اختر واتساب من القائمة ثم أرسل الملف يدوياً.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "فشل تجهيز الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Import flow (SAF "open document") ----------

    fun startImportFlow() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel"
                )
            )
        }
        openDocumentLauncher.launch(intent)
    }

    private fun orgJsonStringLiteral(alreadyEscapedJsonString: String): String = alreadyEscapedJsonString
}
