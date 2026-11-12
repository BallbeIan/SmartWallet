package app.smartwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun dayLabel(d: LocalDate): String = when (d) {
    LocalDate.now() -> "TODAY"
    LocalDate.now().minusDays(1) -> "YESTERDAY"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM")).uppercase()
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Num), color = color)
    }
}

@Composable
fun TransactionsScreen(vm: WalletViewModel) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val all by vm.transactions.collectAsState()
    val month by vm.month.collectAsState()
    var filter by remember { mutableStateOf<Account?>(null) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }

    val shown = all.filter { it.inMonth(month) && (filter == null || it.accountId == filter!!.id) }
    val real = shown.filter { it.transferId == null }
    val income = real.filter { it.amountCents > 0 }.sumOf { it.amountCents }
    val expense = -real.filter { it.amountCents < 0 }.sumOf { it.amountCents }
    // What Month end carried into this month from the previous one (negative when it was overspent).
    val carried = shown.filter { it.isCarryIn() }.sumOf { it.amountCents }
    val accountNames = accounts.associate { it.id to it.name }
    val catById = categories.associateBy { it.id }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.fillMaxSize()) {
        Column {
            MonthBar(month, vm::shiftMonth)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("All") }) }
                items(accounts, key = { it.id }) { a -> FilterChip(filter?.id == a.id, { filter = a }, { Text(a.name) }) }
            }
            Surface(Modifier.padding(16.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(16.dp)) {
                    Stat("IN", Money.format(income), IncomeColor, Modifier.weight(1f))
                    Stat("OUT", Money.format(expense), Expense, Modifier.weight(1f))
                    if (carried != 0L) Stat("CARRIED", Money.format(carried), if (carried < 0) Expense else IncomeColor, Modifier.weight(1f))
                    Stat("NET", Money.format(income - expense + carried), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (shown.isEmpty()) item { Text("No transactions this month.", Modifier.padding(16.dp), color = muted) }
                shown.groupBy { it.date }.forEach { (day, list) ->
                    item(key = "day$day") {
                        Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 4.dp)) {
                            Text(dayLabel(LocalDate.ofEpochDay(day)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
                            Text(Money.format(list.filter { it.transferId == null || it.isCarryIn() }.sumOf { it.amountCents }),
                                style = MaterialTheme.typography.labelSmall.merge(Num), color = muted)
                        }
                    }
                    items(list, key = { it.id }) { t ->
                        val cat = catById[t.categoryId]
                        val dot = if (t.transferId != null) muted else cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
                        Row(Modifier.fillMaxWidth().clickable { editing = t }.padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(if (t.isCarry()) t.note else if (t.transferId != null) "Transfer" else cat?.name ?: "Uncategorized", style = MaterialTheme.typography.bodyLarge)
                                Text(listOf(accountNames[t.accountId].orEmpty(), if (t.isCarry()) "" else t.note).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                            }
                            Text(Money.format(t.amountCents), style = MaterialTheme.typography.bodyLarge.merge(Num),
                                color = when {
                                    t.transferId != null -> MaterialTheme.colorScheme.onSurface
                                    t.amountCents > 0 -> IncomeColor
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (accounts.isEmpty()) vm.message.value = "Add an account first" else adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Icon(Icons.Filled.Add, "Add transaction") }
    }

    if (adding) TransactionDialog(null, 0, accounts, categories, vm::saveTxn, vm::transfer, null) { adding = false }
    editing?.let { t ->
        if (t.isAuto()) {
            WalletDialog("Added by Month end", { editing = null }, dismissText = "Close") {
                Text("This entry is calculated automatically from your transactions and is updated whenever you change an earlier month. " +
                    "To stop it, turn off Month end in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (t.transferId != null) {
            ConfirmDialog("Delete transfer?", "Both sides of the transfer are removed.", onConfirm = { vm.deleteTxn(t) }, onDismiss = { editing = null })
        } else {
            TransactionDialog(t, 0, accounts, categories, vm::saveTxn, vm::transfer, { vm.deleteTxn(t) }) { editing = null }
        }
    }
}
