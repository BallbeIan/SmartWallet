package app.smartwallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val IncomeColor: Color get() = Green

fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("LLLL yyyy"))
fun java.time.LocalDate.label(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
fun MonthBar(month: YearMonth, onShift: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(month.label(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

/** A button that opens a menu; items may include null for a "none" choice. */
@Composable
fun <T> Picker(label: String, items: List<T>, selected: T?, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.let(text) ?: "—"}", maxLines = 1)
        }
        DropdownMenu(open, { open = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(text(item)) }, onClick = { onSelect(item); open = false }) }
        }
    }
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
