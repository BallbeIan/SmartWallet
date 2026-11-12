package app.smartwallet.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.time.LocalDate
import java.time.YearMonth

object Money {
    /** ISO code of the chosen currency, or null to follow the phone. Compose state, so changing it redraws every amount. */
    var code by mutableStateOf<String?>(null)
        private set

    fun load(c: Context) { code = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("currency", null) }

    fun setCurrency(c: Context, newCode: String?) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().apply { if (newCode == null) remove("currency") else putString("currency", newCode) }.apply()
        code = newCode
    }

    fun currency(): Currency =
        code?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
            ?: NumberFormat.getCurrencyInstance().currency ?: Currency.getInstance("USD")

    fun symbol(): String = currency().getSymbol()

    // Amounts stay stored as hundredths; only the symbol and grouping change with the currency.
    fun format(cents: Long): String = NumberFormat.getCurrencyInstance().also { it.currency = currency() }.format(cents / 100.0)

    /** Parses what a person types ("12.5", "12,50", "1,234.50"); null if it isn't a number. */
    fun parse(text: String): Long? {
        var t = text.trim()
        if (t.isEmpty()) return null
        t = if ('.' in t) t.replace(",", "") else t.replace(',', '.')
        return try {
            BigDecimal(t).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Plain "12.50" for editing fields and CSV, independent of locale. */
    fun plain(cents: Long): String = BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()
}

/** A month-end "carried over" entry: a same-account transfer pair, so the balance is unchanged. */
fun Txn.isCarry() = transferId?.startsWith(AUTO_CARRY) == true

/** The entry on the 1st that brings the previous month's balance in; it counts toward the new month's net. */
fun Txn.isCarryIn() = isCarry() && note.startsWith("Carried over from")

/** Created and kept up to date by Month end; users cannot edit these. */
fun Txn.isAuto() = transferId?.startsWith(AUTO_CARRY) == true || transferId?.startsWith(AUTO_MOVE) == true

const val AUTO_CARRY = "carry-"
const val AUTO_MOVE = "mend-"

fun Txn.localDate(): LocalDate = LocalDate.ofEpochDay(date)
fun Txn.inMonth(m: YearMonth) = YearMonth.from(localDate()) == m
