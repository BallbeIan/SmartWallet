package app.smartwallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val real = txns.filter { it.transferId == null }
    val byCat = real.filter { it.inMonth(month) && it.amountCents < 0 }
        .groupBy { it.categoryId }.mapValues { (_, v) -> -v.sumOf { it.amountCents } }
        .entries.sortedByDescending { it.value }
    val total = byCat.sumOf { it.value }
    val catById = categories.associateBy { it.id }
    fun colorOf(id: Long?) = catById[id]?.let { Color(it.color) } ?: Gray
    val track = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle("Reports")
        MonthBar(month, vm::shiftMonth)

        SectionHeader("Spending by category")
        Grouped {
            if (total == 0L) {
                Text("No expenses this month.", Modifier.padding(16.dp), color = muted)
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(176.dp)) {
                        val stroke = 20.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
                        var start = -90f
                        byCat.forEach { (id, v) ->
                            // A hair of gap between slices so they read as separate.
                            val sweep = 360f * v / total
                            drawArc(colorOf(id), start, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Spent", style = MaterialTheme.typography.bodySmall, color = muted)
                        Text(Money.format(total), style = MaterialTheme.typography.titleLarge.merge(Num))
                    }
                }
                byCat.forEach { (id, v) ->
                    Hairline()
                    ListRow(catById[id]?.name ?: "Uncategorized", leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(colorOf(id))) },
                        trailing = { Text("${Money.format(v)}  ${100 * v / total}%", style = MaterialTheme.typography.bodyMedium.merge(Num), color = muted) })
                }
            }
        }

        SectionHeader("Last 6 months")
        val months = (5 downTo 0L).map { month.minusMonths(it) }
        val sums = months.map { m ->
            val inM = real.filter { it.inMonth(m) }
            inM.filter { it.amountCents > 0 }.sumOf { it.amountCents } to -inM.filter { it.amountCents < 0 }.sumOf { it.amountCents }
        }
        val max = (sums.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L).coerceAtLeast(1L)
        Grouped {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
                    sums.forEach { (inc, exp) ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.width(11.dp).height((140f * inc / max).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(IncomeText))
                            Spacer(Modifier.width(3.dp))
                            Box(Modifier.width(11.dp).height((140f * exp / max).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Expense))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    months.forEach { Text(it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center) }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(IncomeText)); Text("Income", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = muted) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(Expense)); Text("Expenses", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = muted) }
                }
            }
        }
    }
}
