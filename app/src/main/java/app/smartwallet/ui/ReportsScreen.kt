package app.smartwallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReportsScreen(vm: WalletViewModel) {
    val categories by vm.categories.collectAsState()
    val txns by vm.transactions.collectAsState()
    val month by vm.month.collectAsState()

    val real = txns.filter { it.transferId == null }
    val byCat = real.filter { it.inMonth(month) && it.amountCents < 0 }
        .groupBy { it.categoryId }.mapValues { (_, v) -> -v.sumOf { it.amountCents } }
        .entries.sortedByDescending { it.value }
    val total = byCat.sumOf { it.value }
    val catById = categories.associateBy { it.id }
    val gray = Color(0xFF9E9E9E)
    fun colorOf(id: Long?) = catById[id]?.let { Color(it.color) } ?: gray

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        MonthBar(month, vm::shiftMonth)
        Text("Spending by category", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        if (total == 0L) {
            Text("No expenses this month.", Modifier.padding(16.dp))
        } else {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(170.dp)) {
                    val stroke = 22.dp.toPx()
                    var start = -90f
                    byCat.forEach { (id, v) ->
                        val sweep = 360f * v / total
                        drawArc(colorOf(id), start, sweep, false, Offset(stroke / 2, stroke / 2),
                            Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                        start += sweep
                    }
                }
                Text(Money.format(total), style = MaterialTheme.typography.titleMedium)
            }
            byCat.forEach { (id, v) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(colorOf(id)))
                    Text(catById[id]?.name ?: "Uncategorized", Modifier.weight(1f).padding(start = 8.dp))
                    Text("${Money.format(v)}  (${(100 * v / total)}%)")
                }
            }
        }

        Text("Last 6 months", Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp), style = MaterialTheme.typography.titleMedium)
        val months = (5 downTo 0L).map { month.minusMonths(it) }
        val sums = months.map { m ->
            val inM = real.filter { it.inMonth(m) }
            inM.filter { it.amountCents > 0 }.sumOf { it.amountCents } to -inM.filter { it.amountCents < 0 }.sumOf { it.amountCents }
        }
        val max = (sums.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L).coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(140.dp), verticalAlignment = Alignment.Bottom) {
            sums.forEach { (inc, exp) ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(12.dp).height((140f * inc / max).dp).background(IncomeColor))
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.width(12.dp).height((140f * exp / max).dp).background(MaterialTheme.colorScheme.error))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            months.forEach { Text(it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("■ Income", color = IncomeColor, style = MaterialTheme.typography.labelMedium)
            Text("■ Expenses", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
    }
}
