package app.smartwallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*

@Composable
fun BudgetsScreen(vm: WalletViewModel) {
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val txns by vm.transactions.collectAsState()
    val month by vm.month.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val spent = txns.filter { it.inMonth(month) && it.transferId == null && it.amountCents < 0 }
        .groupBy { it.categoryId }.mapValues { (_, v) -> -v.sumOf { it.amountCents } }
    val limits = budgets.associate { it.categoryId to it.limitCents }
    val expenseCats = categories.filter { !it.isIncome }.sortedByDescending { (spent[it.id] ?: 0L) + if (it.id in limits) 1 else 0 }
    val totalLimit = expenseCats.sumOf { limits[it.id] ?: 0L }
    val totalSpent = expenseCats.filter { it.id in limits }.sumOf { spent[it.id] ?: 0L }

    @Composable
    fun Bar(spentC: Long, limit: Long) {
        val ratio = spentC.toFloat() / limit
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = when { ratio > 1f -> Expense; ratio > 0.85f -> Amber; else -> Green },
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp, drawStopIndicator = {},
        )
    }

    Column {
        MonthBar(month, vm::shiftMonth)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(20.dp, 8.dp, 20.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BUDGETED SPENDING", style = MaterialTheme.typography.labelSmall, color = muted)
                    Text("${Money.format(totalSpent)} / ${Money.format(totalLimit)}", style = MaterialTheme.typography.headlineMedium.merge(Num))
                    if (totalLimit > 0) Bar(totalSpent, totalLimit)
                }
            }
            item { Text("TAP A CATEGORY TO SET ITS MONTHLY LIMIT", Modifier.padding(20.dp, 0.dp, 20.dp, 4.dp), style = MaterialTheme.typography.labelSmall, color = muted) }
            items(expenseCats, key = { it.id }) { c ->
                val limit = limits[c.id]
                val s = spent[c.id] ?: 0L
                Column(Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(if (limit != null) "${Money.format(s)} / ${Money.format(limit)}" else Money.format(s),
                            style = MaterialTheme.typography.bodyMedium.merge(Num), color = if (limit != null && s > limit) Expense else muted)
                    }
                    if (limit != null) Bar(s, limit)
                }
            }
        }
    }

    editing?.let { c ->
        AmountDialog("Monthly budget: ${c.name}", limits[c.id], onSave = { vm.setBudget(c.id, it) }, onDismiss = { editing = null })
    }
}
