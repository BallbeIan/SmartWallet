package app.smartwallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun dayLabel(d: LocalDate): String = when (d) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleMedium.merge(Num), color = color, maxLines = 1)
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

    Column {
        LargeTitle("History") {
            IconButton({ if (accounts.isEmpty()) vm.message.value = "Add an account first" else adding = true }) { Icon(Icons.Rounded.Add, "Add transaction", tint = Green, modifier = Modifier.size(28.dp)) }
        }
        MonthBar(month, vm::shiftMonth)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Choice("All", filter == null, { filter = null }) }
            items(accounts, key = { it.id }) { a -> Choice(a.name, filter?.id == a.id, { filter = a }) }
        }
        LazyColumn(contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)) {
            item {
                Grouped {
                    // With a carried-over amount there are four figures, which need two rows to stay readable.
                    val net = income - expense + carried
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 13.dp)) {
                        Stat("In", Money.format(income), IncomeText, Modifier.weight(1f))
                        VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Stat("Out", Money.format(expense), Expense, Modifier.weight(1f))
                        if (carried == 0L) {
                            VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Stat("Net", Money.format(net), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                        }
                    }
                    if (carried != 0L) {
                        Hairline(0.dp)
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 13.dp)) {
                            Stat("Carried over", Money.format(carried), if (carried < 0) Expense else IncomeText, Modifier.weight(1f))
                            VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Stat("Net", Money.format(net), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { Text("No transactions this month.", Modifier.padding(20.dp), color = muted) }
            shown.groupBy { it.date }.forEach { (day, list) ->
                item(key = "day$day") {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 7.dp)) {
                            Text(dayLabel(LocalDate.ofEpochDay(day)).uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
                            Text(Money.format(list.filter { it.transferId == null || it.isCarryIn() }.sumOf { it.amountCents }),
                                style = MaterialTheme.typography.labelSmall.merge(Num), color = muted)
                        }
                        Grouped {
                            list.forEachIndexed { i, t ->
                                if (i > 0) Hairline(64.dp)
                                val cat = catById[t.categoryId]
                                ListRow(
                                    title = if (t.isCarry()) t.note else if (t.transferId != null) "Transfer" else cat?.name ?: "Uncategorized",
                                    subtitle = listOf(accountNames[t.accountId].orEmpty(), if (t.isCarry()) "" else t.note).filter { it.isNotBlank() }.joinToString(" · "),
                                    onClick = { editing = t },
                                    leading = {
                                        if (t.transferId != null) Squircle(Icons.Rounded.SwapHoriz, Blue, 36.dp)
                                        else LetterBadge(cat?.name ?: "?", cat?.let { Color(it.color) } ?: Gray)
                                    },
                                    trailing = {
                                        Text(Money.format(t.amountCents), style = MaterialTheme.typography.bodyLarge.merge(Num),
                                            color = if (t.transferId == null && t.amountCents > 0) IncomeText else MaterialTheme.colorScheme.onSurface)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
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
