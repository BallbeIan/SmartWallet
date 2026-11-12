package app.smartwallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*

const val TAB_HOME = 0
const val TAB_HISTORY = 1
const val TAB_BUDGETS = 2
const val TAB_REPORTS = 3
const val TAB_ACCOUNTS = 4

@Composable
private fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun IconBadge(icon: ImageVector, tint: Color, size: Int = 44, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)) {
    Box(Modifier.size(size.dp).clip(shape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, border = cardBorder()) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Text(label, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun LinkCard(title: String, subtitle: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, border = cardBorder()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FlowStat(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint)
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium.merge(Num), maxLines = 1)
        }
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

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SmartWallet", Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
            IconButton(openSettings) { Icon(Icons.Filled.Settings, "Settings", tint = muted) }
        }

        // Hero card: balance, month switcher, and the month's inflow/outflow.
        val heroShape = MaterialTheme.shapes.large
        Column(
            Modifier.fillMaxWidth().clip(heroShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, heroShape)
                .background(Brush.linearGradient(listOf(Color(0xFF18223A), Color(0xFF0E1425))))
                .drawBehind {
                    drawCircle(Green.copy(alpha = 0.16f), size.width * 0.22f, Offset(size.width * 0.95f, size.height * 0.02f))
                    drawCircle(Blue.copy(alpha = 0.14f), size.width * 0.2f, Offset(size.width * 0.02f, size.height * 0.98f))
                }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Green))
                Text("TOTAL BALANCE", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = muted)
                Row(
                    Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", Modifier.size(28.dp).clip(CircleShape).clickable { vm.shiftMonth(-1) }.padding(4.dp))
                    Text(month.label(), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", Modifier.size(28.dp).clip(CircleShape).clickable { vm.shiftMonth(1) }.padding(4.dp))
                }
            }
            Text(Money.format(balances.values.sum()), Modifier.padding(top = 18.dp, bottom = 16.dp),
                style = MaterialTheme.typography.displaySmall.merge(Num), maxLines = 1)
            // Which account's money is being tracked below.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                item { Choice("All accounts", selected == null, { vm.selectedAccountId.value = null }) }
                items(accounts, key = { it.id }) { a -> Choice(a.name, selected?.id == a.id, { vm.selectedAccountId.value = a.id }) }
            }
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (selected != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.name.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                        val b = balances[selected.id] ?: 0L
                        Text(Money.format(b), style = MaterialTheme.typography.titleLarge.merge(Num), color = if (b < 0) Expense else MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowStat("Inflow", Money.format(inflow), Icons.Filled.ArrowDownward, Green, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    FlowStat("Outflow", Money.format(outflow), Icons.Filled.ArrowUpward, Expense, Modifier.weight(1f).padding(start = 14.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Expense", Icons.Filled.ArrowUpward, Color(0xFFE8365D), Modifier.weight(1f)) { openDialog(0) }
            ActionTile("Income", Icons.Filled.ArrowDownward, Color(0xFF14A86F), Modifier.weight(1f)) { openDialog(1) }
            ActionTile("Transfer", Icons.Filled.SwapHoriz, Blue, Modifier.weight(1f)) { if (accounts.size < 2) vm.message.value = "Transfers need two accounts" else openDialog(2) }
            ActionTile("Reports", Icons.Filled.PieChart, Teal, Modifier.weight(1f)) { go(TAB_REPORTS) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkCard("Accounts", "${accounts.size} total", Icons.Filled.AccountBalanceWallet, Green, Modifier.weight(1f)) { go(TAB_ACCOUNTS) }
            LinkCard("Scheduled", "${recurring.count { it.active }} active", Icons.Filled.Autorenew, Amber, Modifier.weight(1f), openScheduled)
        }

        // Budget banner: a call to action until a budget exists, then month progress.
        Surface({ go(TAB_BUDGETS) }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, border = cardBorder()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Savings, Green, 52)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    if (limits.isEmpty()) {
                        Text("No Monthly Budget Set", style = MaterialTheme.typography.titleMedium)
                        Text("Set limits to track your spending", style = MaterialTheme.typography.bodySmall, color = muted)
                    } else {
                        val ratio = budgetSpent.toFloat() / budgetTotal
                        Text("${Money.format(budgetSpent)} of ${Money.format(budgetTotal)}", style = MaterialTheme.typography.titleMedium.merge(Num))
                        LinearProgressIndicator(
                            { ratio.coerceIn(0f, 1f) }, Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = when { ratio > 1f -> Expense; ratio > 0.85f -> Amber; else -> Green },
                            trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {},
                        )
                    }
                }
                Text(if (limits.isEmpty()) "Set Budget" else "Details", color = Green, style = MaterialTheme.typography.titleMedium)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected == null) "Recent Transactions" else "Recent · ${selected.name}", Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
            TextButton({ go(TAB_HISTORY) }) { Text("See all", color = Green) }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, border = cardBorder()) {
            val recent = inScope.take(6)
            if (recent.isEmpty()) {
                Text("No transactions yet. Tap Expense or Income to add one.", Modifier.padding(24.dp), color = muted)
            } else Column {
                recent.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val cat = catById[t.categoryId]
                    val tint = if (t.transferId != null) Blue else cat?.let { Color(it.color) } ?: muted
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (t.transferId != null) IconBadge(Icons.Filled.SwapHoriz, tint, 40, CircleShape)
                        else Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text((cat?.name ?: "?").take(1), color = tint, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(if (t.isCarry()) t.note else if (t.transferId != null) "Transfer" else cat?.name ?: "Uncategorized", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(listOf(accountNames[t.accountId].orEmpty(), t.localDate().label()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                        }
                        Text(Money.format(t.amountCents), style = MaterialTheme.typography.titleMedium.merge(Num),
                            color = if (t.transferId == null && t.amountCents > 0) Green else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    dialogKind?.let { k -> TransactionDialog(null, k, accounts, categories, vm::saveTxn, vm::transfer, null, onDismiss = { dialogKind = null }, defaultAccountId = selected?.id) }
}
