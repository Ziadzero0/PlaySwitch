package com.playswitch.ziadzero.root

/**
 * Enforces smart compatibility boundaries between safe version adaptations
 * (e.g. missing optional legacy tables or newer Play preference layouts)
 * and hard-stop guards when core Android/Play schemas change incompatibly.
 */
object SchemaGuard {
    data class ValidationReport(
        val tables: List<String>,
        val blockingIssues: List<String>,
        val adaptiveNotes: List<String>,
        val accountAuxTables: List<String> = emptyList(),
        val hasUpdateAccountColumn: Boolean = false
    ) {
        val isSafe: Boolean get() = blockingIssues.isEmpty()
    }

    fun validateAccountDb(db: SqliteDbGateway, dbLabel: String): ValidationReport {
        val tables = db.listTables()
        val blocking = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if ("accounts" !in tables) {
            blocking += "$dbLabel missing core 'accounts' table"
            return ValidationReport(tables, blocking, notes)
        }

        val accCols = db.listColumns("accounts")
        if ("_id" !in accCols || "name" !in accCols || "type" !in accCols) {
            blocking += "$dbLabel 'accounts' table missing expected columns (_id, name, type)"
            return ValidationReport(tables, blocking, notes)
        }

        val auxTables = mutableListOf<String>()
        for (table in tables) {
            if (table == "accounts") continue
            val cols = db.listColumns(table)
            val fkToAccounts = db.foreignKeyReferences(table).filter {
                it.referencedTable.equals("accounts", ignoreCase = true)
            }

            val unsupportedFk = fkToAccounts.firstOrNull { fk ->
                !fk.fromColumn.equals("accounts_id", ignoreCase = true) ||
                    (fk.toColumn.isNotEmpty() && !fk.toColumn.equals("_id", ignoreCase = true))
            }
            if (unsupportedFk != null) {
                blocking += "$dbLabel '$table' uses unsupported FK ${unsupportedFk.fromColumn} -> accounts(${unsupportedFk.toColumn})"
                continue
            }

            if ("accounts_id" in cols || fkToAccounts.isNotEmpty()) {
                auxTables += table
            }
        }

        if ("extras" !in tables && dbLabel == "DE DB") {
            notes += "Device account store omits legacy 'extras' table (standard modern Android layout)"
        }

        return ValidationReport(
            tables = tables,
            blockingIssues = blocking,
            adaptiveNotes = notes,
            accountAuxTables = auxTables
        )
    }

    fun validatePlayAppStateDb(db: SqliteDbGateway): ValidationReport {
        val tables = db.listTables()
        val blocking = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if ("appstate" !in tables) {
            blocking += "Play Store localappstate database missing 'appstate' table"
            return ValidationReport(tables, blocking, notes)
        }

        val cols = db.listColumns("appstate")
        if ("package_name" !in cols || "account" !in cols) {
            blocking += "Play Store 'appstate' table missing expected columns (package_name, account)"
            return ValidationReport(tables, blocking, notes)
        }

        val hasUpdateCol = "account_for_update" in cols
        if (!hasUpdateCol) {
            notes += "Play Store omits separate update-account column; unified account mode active"
        }

        return ValidationReport(
            tables = tables,
            blockingIssues = blocking,
            adaptiveNotes = notes,
            hasUpdateAccountColumn = hasUpdateCol
        )
    }

    fun validatePlayLibraryDb(db: SqliteDbGateway): ValidationReport {
        val tables = db.listTables()
        val blocking = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if ("ownership" !in tables) {
            blocking += "Play Store library database schema changed ('ownership' table missing)"
            return ValidationReport(tables, blocking, notes)
        }

        val cols = db.listColumns("ownership")
        if ("doc_id" !in cols || "account" !in cols) {
            blocking += "Play Store 'ownership' table missing expected columns (doc_id, account)"
        }

        return ValidationReport(tables, blocking, notes)
    }
}
