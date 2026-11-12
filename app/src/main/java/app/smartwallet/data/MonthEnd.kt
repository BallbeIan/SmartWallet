package app.smartwallet.data

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** What to do with the spending account's balance when a month ends. Stored in preferences. */
object MonthEnd {
    data class Rules(
        val enabled: Boolean = false,
        val spending: Long? = null,
        val moveSurplus: Boolean = false,
        val surplusTarget: Long? = null,
        val coverDeficit: Boolean = false,
        val deficitSource: Long? = null,
        val keepCents: Long = 0, // stays in the spending account; only the excess above it is moved
    )

    private const val PREFS = "monthend"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun android.content.SharedPreferences.id(key: String) = getLong(key, -1).takeIf { it >= 0 }

    fun load(c: Context): Rules = prefs(c).run {
        // Rules saved before the switch existed count as on when a spending account was chosen.
        Rules(if (contains("enabled")) getBoolean("enabled", false) else id("spending") != null, id("spending"), getBoolean("move_surplus", false), id("surplus_target"), getBoolean("cover_deficit", false), id("deficit_source"), getLong("keep", 0))
    }

    fun save(c: Context, r: Rules) = prefs(c).edit().apply {
        putBoolean("enabled", r.enabled); putLong("spending", r.spending ?: -1); putBoolean("move_surplus", r.moveSurplus); putLong("surplus_target", r.surplusTarget ?: -1)
        putBoolean("cover_deficit", r.coverDeficit); putLong("deficit_source", r.deficitSource ?: -1); putLong("keep", r.keepCents)
    }.apply()

}

/**
 * The month-end entries that should exist, derived from the real (manual) transactions for every ended month since the first one.
 * Pure function: the same input always gives the same entries, so editing an old month simply changes what this returns.
 * Entries are same-account "carried over" pairs plus optional move/cover transfers, all marked by their transferId prefix.
 */
fun computeMonthEnd(rules: MonthEnd.Rules, accounts: List<Account>, manual: List<Txn>, today: LocalDate): List<Txn> {
    if (!rules.enabled || manual.isEmpty()) return emptyList()
    val account = accounts.firstOrNull { it.id == rules.spending } ?: return emptyList()
    val target = accounts.firstOrNull { it.id == rules.surplusTarget && it.id != account.id }
    val source = accounts.firstOrNull { it.id == rules.deficitSource && it.id != account.id }
    val own = manual.filter { it.accountId == account.id }
    val monthName = { ym: YearMonth -> ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val out = mutableListOf<Txn>()

    val lastEnded = YearMonth.from(today).minusMonths(1)
    var m = manual.minOf { YearMonth.from(it.localDate()) }
    while (!m.isAfter(lastEnded)) {
        val day = m.atEndOfMonth().toEpochDay()
        // Earlier months' own entries count too, so each month starts from what the previous one left behind.
        val balance = account.initialCents + own.filter { it.date <= day }.sumOf { it.amountCents } +
            out.filter { it.accountId == account.id && it.date <= day }.sumOf { it.amountCents }

        fun transfer(from: Long, to: Long, cents: Long, note: String) {
            val id = "$AUTO_MOVE${UUID.randomUUID()}"
            out += Txn(accountId = from, categoryId = null, amountCents = -cents, note = note, date = day, transferId = id)
            out += Txn(accountId = to, categoryId = null, amountCents = cents, note = note, date = day, transferId = id)
        }
        val movable = balance - rules.keepCents
        var remaining = balance // what is still in the account after the move or cover
        if (movable > 0 && rules.moveSurplus && target != null) { transfer(account.id, target.id, movable, "Month-end surplus"); remaining = balance - movable }
        else if (balance < 0 && rules.coverDeficit && source != null) { transfer(source.id, account.id, -balance, "Month-end cover"); remaining = 0 }

        // Money that stays is shown as a transfer from this month into the next. The pair cancels out, so balances are untouched.
        if (remaining != 0L) {
            val id = "$AUTO_CARRY${UUID.randomUUID()}"
            out += Txn(accountId = account.id, categoryId = null, amountCents = -remaining, note = "Carried over to ${monthName(m.plusMonths(1))}", date = day, transferId = id)
            out += Txn(accountId = account.id, categoryId = null, amountCents = remaining, note = "Carried over from ${monthName(m)}", date = m.plusMonths(1).atDay(1).toEpochDay(), transferId = id)
        }
        m = m.plusMonths(1)
    }
    return out
}

/** Makes the stored month-end entries match [computeMonthEnd]; does nothing (and writes nothing) when they already do. */
suspend fun reconcileMonthEnd(db: WalletDb, c: Context) {
    val rules = MonthEnd.load(c)
    val dao = db.dao()
    db.withTransaction {
        val all = dao.transactionsOnce()
        val existing = all.filter { it.isAuto() }
        val desired = computeMonthEnd(rules, dao.accountsOnce(), all.filter { !it.isAuto() }, LocalDate.now())
        fun key(t: Txn) = "${t.accountId}|${t.amountCents}|${t.date}|${t.note}"
        if (existing.map(::key).sorted() == desired.map(::key).sorted()) return@withTransaction
        dao.deleteAutoTransactions()
        dao.insertTxns(desired)
    }
}
