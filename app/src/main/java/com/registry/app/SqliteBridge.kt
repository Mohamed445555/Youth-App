package com.registry.app

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real on-device SQLite, exposed to the WebView's JS as a synchronous bridge.
 * The web app's runQuery()/runExec() calls are routed here instead of sql.js/WASM.
 */
class RegistryDbHelper(context: Context) :
    SQLiteOpenHelper(context, "registry.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS people (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT,
              surname TEXT,
              age INTEGER,
              city TEXT,
              origin_city TEXT,
              region TEXT,
              tribe TEXT,
              id_number TEXT,
              education TEXT,
              notes TEXT,
              phone TEXT,
              phone2 TEXT,
              registry_id TEXT,
              created_at TEXT DEFAULT (datetime('now'))
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cities (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT UNIQUE,
              region TEXT,
              auto_added INTEGER DEFAULT 0
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No destructive migrations yet; columns are added defensively in onCreate/onOpen.
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureColumn(db, "people", "registry_id", "TEXT")
        ensureColumn(db, "cities", "auto_added", "INTEGER DEFAULT 0")
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, type: String) {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        cursor.use {
            val nameIdx = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (it.getString(nameIdx) == column) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
        }
    }
}

class SqliteBridge(private val context: Context) {

    private val dbHelper = RegistryDbHelper(context)

    /**
     * Runs a SELECT and returns a JSON array of row objects, mirroring
     * sql.js's stmt.getAsObject() shape used by the web app's runQuery().
     */
    @JavascriptInterface
    fun query(sql: String, paramsJson: String): String {
        val db = dbHelper.readableDatabase
        val params = jsonArrayToStringArray(paramsJson)
        val cursor: Cursor = db.rawQuery(sql, params)
        val result = JSONArray()
        cursor.use {
            val columnNames = it.columnNames
            while (it.moveToNext()) {
                val row = JSONObject()
                for (col in columnNames) {
                    val idx = it.getColumnIndex(col)
                    when (it.getType(idx)) {
                        Cursor.FIELD_TYPE_INTEGER -> row.put(col, it.getLong(idx))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(col, it.getDouble(idx))
                        Cursor.FIELD_TYPE_NULL -> row.put(col, JSONObject.NULL)
                        else -> row.put(col, it.getString(idx))
                    }
                }
                result.put(row)
            }
        }
        return result.toString()
    }

    /**
     * Runs INSERT/UPDATE/DELETE/DDL. Multiple statements separated by ';' are
     * supported (used once, for initial schema creation).
     */
    @JavascriptInterface
    fun exec(sql: String, paramsJson: String): String {
        val db = dbHelper.writableDatabase
        return try {
            val params = jsonArrayToStringArray(paramsJson)
            if (params.isEmpty()) {
                db.execSQL(sql)
            } else {
                db.execSQL(sql, params)
            }
            JSONObject().put("ok", true).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "unknown error").toString()
        }
    }

 private fun jsonArrayToStringArray(paramsJson: String): Array<String?> {
        if (paramsJson.isBlank()) return arrayOf()
        val arr = JSONArray(paramsJson)
        return Array(arr.length()) { i ->
            if (arr.isNull(i)) null else arr.get(i).toString()
        }
    }
}
