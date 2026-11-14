package app.smartwallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.smartwallet.data.BackupInfo
import app.smartwallet.data.Counts
import app.smartwallet.data.Parts

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** One line with a switch: what it is, how much of it there is, and whether it is included. */
@Composable
private fun PartRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    ListRow(title, subtitle = subtitle, onClick = { onChange(!checked) }, trailing = { AppSwitch(checked, onChange) })

private data class PartLine(val key: String, val title: String, val subtitle: String)

@Composable
private fun PartsCard(lines: List<PartLine>, parts: Parts, onChange: (Parts) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = SheetCard) {
        Column {
            lines.forEachIndexed { i, l ->
                if (i > 0) Hairline()
                val on = when (l.key) { "accounts" -> parts.accounts; "categories" -> parts.categories; "transactions" -> parts.transactions
                    "budgets" -> parts.budgets; "scheduled" -> parts.scheduled; else -> parts.settings }
                PartRow(l.title, l.subtitle, on) { v ->
                    onChange(when (l.key) { "accounts" -> parts.copy(accounts = v); "categories" -> parts.copy(categories = v); "transactions" -> parts.copy(transactions = v)
                        "budgets" -> parts.copy(budgets = v); "scheduled" -> parts.copy(scheduled = v); else -> parts.copy(settings = v) })
                }
            }
        }
    }
}

/** Choose what to save, and whether it goes into a backup or a spreadsheet. */
@Composable
fun ExportDialog(counts: Counts, onExport: (csv: Boolean, parts: Parts) -> Unit, onDismiss: () -> Unit) {
    var csv by remember { mutableStateOf(false) }
    var parts by remember { mutableStateOf(Parts()) }
    val lines = listOf(
        PartLine("accounts", "Accounts", plural(counts.accounts, "account", "accounts") + ", with their starting balances"),
        PartLine("categories", "Categories", plural(counts.categories, "category", "categories")),
        PartLine("transactions", "Transactions", plural(counts.transactions, "transaction", "transactions")),
        PartLine("budgets", "Budgets", plural(counts.budgets, "budget", "budgets")),
        PartLine("scheduled", "Scheduled payments", plural(counts.scheduled, "payment or transfer", "payments and transfers")),
        PartLine("settings", "Settings", "Currency, theme, reminders and month end rules"),
    )
    WalletDialog("Export", onDismiss, "Export", confirmEnabled = csv || parts.any, onConfirm = { onExport(csv, parts); onDismiss() }) {
        SectionLabel("Format")
        ChoiceGroup(listOf(false, true), { it == csv }, { if (it) "Spreadsheet (.csv)" else "Backup (.json)" }) { csv = it }
        Text(
            if (csv) "A CSV file with your transactions only, to open in a spreadsheet." else "One file you can import into SmartWallet again, on this phone or another.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!csv) {
            SectionLabel("Include")
            PartsCard(lines, parts) { parts = it }
            Text("The file is not encrypted, so keep it somewhere safe. Your PIN is never included.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shows what a backup file holds and lets the user pick which parts to bring in. */
@Composable
fun ImportDialog(info: BackupInfo, onImport: (Parts) -> Unit, onDismiss: () -> Unit) {
    val counts = info.counts
    var parts by remember {
        mutableStateOf(Parts(info.has("accounts"), info.has("categories"), info.has("transactions"), info.has("budgets"), info.has("scheduled"), info.settings != null))
    }
    val lines = listOfNotNull(
        if (info.has("accounts")) PartLine("accounts", "Accounts", plural(counts.accounts, "account", "accounts")) else null,
        if (info.has("categories")) PartLine("categories", "Categories", plural(counts.categories, "category", "categories")) else null,
        if (info.has("transactions")) PartLine("transactions", "Transactions", plural(counts.transactions, "transaction", "transactions")) else null,
        if (info.has("budgets")) PartLine("budgets", "Budgets", plural(counts.budgets, "budget", "budgets")) else null,
        if (info.has("scheduled")) PartLine("scheduled", "Scheduled payments", plural(counts.scheduled, "payment or transfer", "payments and transfers")) else null,
        if (info.settings != null) PartLine("settings", "Settings", "Currency, theme, reminders and month end rules") else null,
    )
    val exported = info.json.optString("exported")
    WalletDialog("Import", onDismiss, "Import", confirmEnabled = parts.any && lines.isNotEmpty(), onConfirm = { onImport(parts) }) {
        Text(
            "This backup" + (if (exported.isNotBlank()) ", saved on $exported," else "") + " contains the items below. Pick what to bring in.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lines.isEmpty()) Text("The file has no data in it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else PartsCard(lines, parts) { parts = it }
        Text("Importing adds to what is already in the app and never overwrites it. Anything that is already there is skipped, so importing the same file twice is safe.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
