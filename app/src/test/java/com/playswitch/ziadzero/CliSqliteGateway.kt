package com.playswitch.ziadzero

import com.playswitch.ziadzero.root.ForeignKeyInfo
import com.playswitch.ziadzero.root.SqliteDbGateway
import java.io.File

class CliSqliteGateway(private val dbFile: File) : SqliteDbGateway {
    private val sqliteBin: String = listOf(
        "/data/user/0/com.agy/files/usr/bin/sqlite3",
        "sqlite3"
    ).firstOrNull { path ->
        path == "sqlite3" || File(path).canExecute()
    } ?: error("sqlite3 binary not found for unit tests")

    override fun listTables(): List<String> =
        queryRows(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ).mapNotNull { it.firstOrNull()?.toString() }

    override fun listColumns(tableName: String): Set<String> =
        queryNamedRows("PRAGMA table_info($tableName)")
            .mapNotNull { it["name"]?.toString() }
            .toSet()

    override fun foreignKeyReferences(tableName: String): List<ForeignKeyInfo> =
        queryNamedRows("PRAGMA foreign_key_list($tableName)").mapNotNull { row ->
            val refTable = row["table"]?.toString().orEmpty()
            val fromCol = row["from"]?.toString().orEmpty()
            val toCol = row["to"]?.toString().orEmpty()
            if (refTable.isEmpty() || fromCol.isEmpty()) {
                null
            } else {
                ForeignKeyInfo(
                    id = (row["id"] as? Number)?.toInt() ?: 0,
                    seq = (row["seq"] as? Number)?.toInt() ?: 0,
                    referencedTable = refTable,
                    fromColumn = fromCol,
                    toColumn = toCol
                )
            }
        }

    override fun execSql(sql: String, bindArgs: List<Any?>) {
        val rendered = bindParams(sql, bindArgs)
        runSqlite(listOf(dbFile.absolutePath), rendered)
    }

    override fun queryRows(sql: String, bindArgs: List<Any?>): List<List<Any?>> =
        queryNamedRows(sql, bindArgs).map { row -> row.values.toList() }

    fun queryNamedRows(sql: String, bindArgs: List<Any?> = emptyList()): List<LinkedHashMap<String, Any?>> {
        val rendered = bindParams(sql, bindArgs)
        val out = runSqlite(listOf("-json", dbFile.absolutePath), rendered).trim()
        if (out.isEmpty()) return emptyList()
        return OrderedJsonParser(out).parseArrayOfObjects()
    }

    override fun inTransaction(block: () -> Unit) {
        val snapshot = if (dbFile.exists()) dbFile.readBytes() else null
        try {
            block()
        } catch (t: Throwable) {
            if (snapshot != null) {
                dbFile.writeBytes(snapshot)
            }
            throw t
        }
    }

    override fun checkpointWalTruncate() {
        runSqlite(listOf(dbFile.absolutePath), "PRAGMA wal_checkpoint(TRUNCATE);")
    }

    override fun integrityCheck(): String {
        val rows = queryRows("PRAGMA integrity_check;")
        return rows.firstOrNull()?.firstOrNull()?.toString().orEmpty()
    }

    override fun foreignKeyCheck(): List<String> =
        queryNamedRows("PRAGMA foreign_key_check;").map { row ->
            val tbl = row["table"]?.toString() ?: "?"
            val rowId = row["rowid"]?.toString() ?: "-1"
            val parent = row["parent"]?.toString() ?: "?"
            "$tbl(rowid=$rowId)->$parent"
        }

    private fun runSqlite(args: List<String>, sql: String): String {
        val cmd = listOf(sqliteBin) + args + sql
        val process = ProcessBuilder(cmd).redirectErrorStream(false).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0) {
            error("sqlite3 exited with $code: $stderr (SQL: $sql)")
        }
        return stdout
    }

    companion object {
        fun bindParams(sql: String, bindArgs: List<Any?>): String {
            if (bindArgs.isEmpty()) return sql
            val sb = StringBuilder()
            var argIndex = 0
            var inSingleQuote = false
            var i = 0
            while (i < sql.length) {
                val ch = sql[i]
                if (ch == '\'') {
                    sb.append(ch)
                    if (inSingleQuote && i + 1 < sql.length && sql[i + 1] == '\'') {
                        sb.append('\'')
                        i++
                    } else {
                        inSingleQuote = !inSingleQuote
                    }
                } else if (ch == '?' && !inSingleQuote) {
                    require(argIndex < bindArgs.size) { "Missing bind arg at index $argIndex" }
                    sb.append(formatLiteral(bindArgs[argIndex++]))
                } else {
                    sb.append(ch)
                }
                i++
            }
            require(argIndex == bindArgs.size) { "Unused bind args: $argIndex vs ${bindArgs.size}" }
            return sb.toString()
        }

        private fun formatLiteral(value: Any?): String = when (value) {
            null -> "NULL"
            is Number -> value.toString()
            is Boolean -> if (value) "1" else "0"
            else -> "'" + value.toString().replace("'", "''") + "'"
        }
    }

    private class OrderedJsonParser(private val src: String) {
        private var pos = 0

        fun parseArrayOfObjects(): List<LinkedHashMap<String, Any?>> {
            skipWs()
            expect('[')
            val list = mutableListOf<LinkedHashMap<String, Any?>>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWs()
                list += parseObject()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseObject(): LinkedHashMap<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                skipWs()
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseValue(): Any? = when (peek()) {
            '"' -> parseString()
            'n' -> {
                pos += 4
                null
            }
            't' -> {
                pos += 4
                true
            }
            'f' -> {
                pos += 5
                false
            }
            else -> parseNumber()
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val esc = src[pos++]
                        when (esc) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < src.length && src[pos] !in charArrayOf(',', '}', ']', ' ', '\n', '\r', '\t')) {
                pos++
            }
            val raw = src.substring(start, pos)
            return if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                raw.toDouble()
            } else {
                raw.toLong()
            }
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = src[pos]

        private fun expect(ch: Char) {
            require(pos < src.length && src[pos] == ch) { "Expected '$ch' at $pos" }
            pos++
        }
    }
}
