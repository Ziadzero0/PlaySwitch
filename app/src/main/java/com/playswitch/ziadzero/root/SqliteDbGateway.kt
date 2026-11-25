package com.playswitch.ziadzero.root

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

data class ForeignKeyInfo(
    val id: Int,
    val seq: Int,
    val referencedTable: String,
    val fromColumn: String,
    val toColumn: String
)

interface SqliteDbGateway {
    fun listTables(): List<String>
    fun listColumns(tableName: String): Set<String>
    fun foreignKeyReferences(tableName: String): List<ForeignKeyInfo>
    fun execSql(sql: String, bindArgs: List<Any?> = emptyList())
    fun queryRows(sql: String, bindArgs: List<Any?> = emptyList()): List<List<Any?>>
    fun inTransaction(block: () -> Unit)
    fun checkpointWalTruncate()
    fun integrityCheck(): String
    fun foreignKeyCheck(): List<String>
}

class AndroidSqliteGateway(private val db: SQLiteDatabase) : SqliteDbGateway {
    override fun listTables(): List<String> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null
        ).use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        return tables
    }

    override fun listColumns(tableName: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($tableName)", null).use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (idx >= 0) cols += c.getString(idx)
            }
        }
        return cols
    }

    override fun foreignKeyReferences(tableName: String): List<ForeignKeyInfo> {
        val list = mutableListOf<ForeignKeyInfo>()
        db.rawQuery("PRAGMA foreign_key_list($tableName)", null).use { c ->
            val idIdx = c.getColumnIndex("id")
            val seqIdx = c.getColumnIndex("seq")
            val tableIdx = c.getColumnIndex("table")
            val fromIdx = c.getColumnIndex("from")
            val toIdx = c.getColumnIndex("to")
            while (c.moveToNext()) {
                val refTable = if (tableIdx >= 0) c.getString(tableIdx).orEmpty() else ""
                val fromCol = if (fromIdx >= 0) c.getString(fromIdx).orEmpty() else ""
                val toCol = if (toIdx >= 0) c.getString(toIdx).orEmpty() else ""
                if (refTable.isNotEmpty() && fromCol.isNotEmpty()) {
                    list += ForeignKeyInfo(
                        id = if (idIdx >= 0) c.getInt(idIdx) else 0,
                        seq = if (seqIdx >= 0) c.getInt(seqIdx) else 0,
                        referencedTable = refTable,
                        fromColumn = fromCol,
                        toColumn = toCol
                    )
                }
            }
        }
        return list
    }

    override fun execSql(sql: String, bindArgs: List<Any?>) {
        if (bindArgs.isEmpty()) {
            db.execSQL(sql)
        } else {
            db.execSQL(sql, bindArgs.toTypedArray())
        }
    }

    override fun queryRows(sql: String, bindArgs: List<Any?>): List<List<Any?>> {
        val args = bindArgs.map { it?.toString() }.toTypedArray()
        return db.rawQuery(sql, args).use { c ->
            val rows = mutableListOf<List<Any?>>()
            while (c.moveToNext()) {
                rows += List(c.columnCount) { idx ->
                    when (c.getType(idx)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(idx)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(idx)
                        Cursor.FIELD_TYPE_STRING -> c.getString(idx)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(idx)
                        else -> c.getString(idx)
                    }
                }
            }
            rows
        }
    }

    override fun inTransaction(block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun checkpointWalTruncate() {
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }
    }

    override fun integrityCheck(): String {
        return db.rawQuery("PRAGMA integrity_check;", null).use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        }
    }

    override fun foreignKeyCheck(): List<String> {
        val violations = mutableListOf<String>()
        db.rawQuery("PRAGMA foreign_key_check;", null).use { c ->
            while (c.moveToNext()) {
                val tbl = runCatching { c.getString(0) }.getOrDefault("?")
                val rowId = runCatching { c.getLong(1) }.getOrDefault(-1L)
                val parent = runCatching { c.getString(2) }.getOrDefault("?")
                violations += "$tbl(rowid=$rowId)->$parent"
            }
        }
        return violations
    }
}
