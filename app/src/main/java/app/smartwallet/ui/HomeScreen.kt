package app.smartwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*

const val TAB_HOME = 0
const val TAB_HISTORY = 1
const val TAB_BUDGETS = 2
const val TAB_REPORTS = 3
const val TAB_ACCOUNTS = 4

/** A round coloured button with its label underneath. */
@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun FlowCell(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleMedium.merge(Num), maxLines = 1)
    }
}

@Composable
fun HomeScreen(vm: WalletViewModel, go: (Int) -> Unit, openSettings: () -> Unit, openScheduled: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val balances by vm.balances.collectAsState()
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    val month by vm.month.collectAsState()
    var dialogKind by remember { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val selectedId by vm.selectedAccountId.collectAsState()
    val selected = accounts.firstOrNull { it.id == selectedId } // null = all accounts
    val inScope = txns.filter { selected == null || it.accountId == selected.id }
    val real = inScope.filter { it.inMonth(month) && it.transferId == null }
    val inflow = real.filter { it.amountCents > 0 }.sumOf { it.amountCents }
    val outflow = -real.filter { it.amountCents < 0 }.sumOf { it.amountCents }
    val catById = categories.associateBy { it.id }
    val accountNames = accounts.associate { it.id to it.name }
    val limits = budgets.filter { catById[it.categoryId]?.isIncome == false }.associate { it.categoryId to it.limitCents }
    val budgetSpent = txns.filter { it.inMonth(month) && it.transferId == null && it.amountCents < 0 && it.categoryId in limits }.sumOf { -it.amountCents }
    val budgetTotal = limits.values.sum()
    fun openDialog(kind: Int) { if (accounts.isEmpty()) vm.message.value = "Add an account first" else dialogKind = kind }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle("Home") { IconButton(openSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Green) } }

        // The total across every account, and the month being looked at. The number gets the full width so cents never clip.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total balance", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = muted)
                MonthPill(month, vm::shiftMonth)
            }
            Text(Money.format(balances.values.sum()), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.displaySmall.merge(Num), maxLines = 1)
        }

        // Which account's money is tracked below.
        LazyRow(Modifier.padding(top = 14.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Choice("All accounts", selected == null, { vm.selectedAccountId.value = null }) }
            items(accounts, key = { it.id }) { a -> Choice(a.name, selected?.id == a.id, { vm.selectedAccountId.value = a.id }) }
        }

        Grouped(Modifier.padding(top = 14.dp)) {
            if (selected != null) {
                val b = balances[selected.id] ?: 0L
                ListRow(selected.name, trailing = { Text(Money.format(b), style = MaterialTheme.typography.titleMedium.merge(Num), color = if (b < 0) Expense else MaterialTheme.colorScheme.onSurface) })
                Hairline()
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 13.dp)) {
                FlowCell("Inflow", Money.format(inflow), Icons.Rounded.ArrowDownward, IncomeText, Modifier.weight(1f))
                VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                FlowCell("Outflow", Money.format(outflow), Icons.Rounded.ArrowUpward, Expense, Modifier.weight(1f))
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 22.dp)) {
            QuickAction("Expense", Icons.Rounded.ArrowUpward, Expense, Modifier.weight(1f)) { openDialog(0) }
            QuickAction("Income", Icons.Rounded.ArrowDownward, Green, Modifier.weight(1f)) { openDialog(1) }
            QuickAction("Transfer", Icons.Rounded.SwapHoriz, Blue, Modifier.weight(1f)) { if (accounts.size < 2) vm.message.value = "Transfers need two accounts" else openDialog(2) }
            QuickAction("Reports", Icons.Rounded.PieChart, Indigo, Modifier.weight(1f)) { go(TAB_REPORTS) }
        }

        SectionHeader("Budget")
        Grouped {
            if (limits.isEmpty()) {
                ListRow("No Monthly Budget Set", subtitle = "Set limits to track your spending", onClick = { go(TAB_BUDGETS) },
                    leading = { Squircle(Icons.Rounded.Savings, Green) }, trailing = { Chevron() })
            } else {
                val ratio = budgetSpent.toFloat() / budgetTotal
                Column(Modifier.fillMaxWidth().clickable { go(TAB_BUDGETS) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Squircle(Icons.Rounded.Savings, Green)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("${Money.format(budgetSpent)} of ${Money.format(budgetTotal)}", style = MaterialTheme.typography.bodyLarge.merge(Num))
                            Text("Spent this month", style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                        Chevron()
                    }
                    ProgressBar(ratio, when { ratio > 1f -> Expense; ratio > 0.85f -> Amber; else -> Green })
                }
            }
        }

        SectionHeader("Manage")
        Grouped {
            ListRow("Accounts", subtitle = "${accounts.size} total", onClick = { go(TAB_ACCOUNTS) }, leading = { Squircle(Icons.Rounded.AccountBalanceWallet, Blue) }, trailing = { Chevron() })
            Hairline(58.dp)
            ListRow("Scheduled payments", subtitle = "${recurring.count { it.active }} active", onClick = openScheduled, leading = { Squircle(Icons.Rounded.Autorenew, Amber) }, trailing = { Chevron() })
        }

        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.Bottom) {
            SectionHeader(if (selected == null) "Recent transactions" else "Recent · ${selected.name}", Modifier.weight(1f))
            TextButton({ go(TAB_HISTORY) }) { Text("See all", color = Green, style = MaterialTheme.typography.bodyMedium) }
        }
        Grouped {
            val recent = inScope.take(6)
            if (recent.isEmpty()) {
                Text("No transactions yet. Tap Expense or Income to add one.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = muted)
            } else recent.forEachIndexed { i, t ->
                if (i > 0) Hairline(64.dp)
                val cat = catById[t.categoryId]
                ListRow(
                    title = if (t.isCarry()) t.note else if (t.transferId != null) "Transfer" else cat?.name ?: "Uncategorized",
                    subtitle = listOf(accountNames[t.accountId].orEmpty(), t.localDate().label()).joinToString(" · "),
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

    dialogKind?.let { k -> TransactionDialog(null, k, accounts, categories, vm::saveTxn, vm::transfer, null, onDismiss = { dialogKind = null }, defaultAccountId = selected?.id) }
}
