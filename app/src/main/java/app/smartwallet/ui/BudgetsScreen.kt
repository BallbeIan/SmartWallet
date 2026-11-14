package app.smartwallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    fun barColor(ratio: Float) = when { ratio > 1f -> Expense; ratio > 0.85f -> Amber; else -> Green }

    Column {
        LargeTitle("Budgets")
        MonthBar(month, vm::shiftMonth)
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            item {
                Grouped {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Budgeted spending", style = MaterialTheme.typography.bodyMedium, color = muted)
                        Text("${Money.format(totalSpent)} of ${Money.format(totalLimit)}", style = MaterialTheme.typography.headlineMedium.merge(Num))
                        if (totalLimit > 0) ProgressBar(totalSpent.toFloat() / totalLimit, barColor(totalSpent.toFloat() / totalLimit))
                    }
                }
            }
            item { SectionHeader("Categories") }
            item {
                Grouped {
                    expenseCats.forEachIndexed { i, c ->
                        if (i > 0) Hairline(16.dp)
                        val limit = limits[c.id]
                        val s = spent[c.id] ?: 0L
                        Column(Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row {
                                Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(if (limit != null) "${Money.format(s)} / ${Money.format(limit)}" else Money.format(s),
                                    style = MaterialTheme.typography.bodyMedium.merge(Num), color = if (limit != null && s > limit) Expense else muted)
                            }
                            if (limit != null) ProgressBar(s.toFloat() / limit, barColor(s.toFloat() / limit), height = 5.dp)
                        }
                    }
                }
            }
            item { Text("Tap a category to set its monthly limit.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = muted) }
        }
    }

    editing?.let { c ->
        AmountDialog("Monthly budget: ${c.name}", limits[c.id], onSave = { vm.setBudget(c.id, it) }, onDismiss = { editing = null })
    }
}
