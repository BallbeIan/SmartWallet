package app.smartwallet

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.smartwallet.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Home's account selector: the explicit "All accounts" choice. Unset (null) means the first account. */
const val ALL_ACCOUNTS = -1L

val CategoryPalette = listOf(
    0xFFFF3B30, 0xFF007AFF, 0xFF34C759, 0xFFFF9500, 0xFFAF52DE, 0xFF30B0C7,
    0xFFFF2D55, 0xFFA2845E, 0xFF5856D6, 0xFF00C7BE, 0xFFFFCC00, 0xFF8E8E93,
)

class WalletViewModel(app: Application) : AndroidViewModel(app) {
    // Declared before the init block: it runs immediately and uses this lock.
    // Startup seeding and the demo loader can run at the same time; the lock keeps the second one from inserting duplicates.
    private val seedLock = kotlinx.coroutines.sync.Mutex()

    private val db = WalletDb.get(app)
    private val dao = db.dao()

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val accounts = dao.accounts().state(emptyList())
    val categories = dao.categories().state(emptyList())
    val transactions = dao.transactions().state(emptyList())
    val budgets = dao.budgets().state(emptyList())
    val recurring = dao.recurring().state(emptyList())

    val balances: StateFlow<Map<Long, Long>> = combine(accounts, transactions) { a, t ->
        val sums = t.groupBy { it.accountId }.mapValues { (_, v) -> v.sumOf { it.amountCents } }
        a.associate { it.id to it.initialCents + (sums[it.id] ?: 0L) }
    }.state(emptyMap())

    val month = MutableStateFlow(YearMonth.now())
    /** Account shown on Home; null means all accounts. */
    val selectedAccountId = MutableStateFlow<Long?>(null)

    private val monthEndTick = MutableStateFlow(0)
    /** Call after the Month end rules change so every past month is recalculated. */
    fun monthEndChanged() { monthEndTick.value++ }
    val message = MutableStateFlow<String?>(null)

    init {
        // Month-end entries are derived data: whenever transactions, accounts or the rules change, recompute them for all months.
        // The short delay lets a burst of edits settle, and reconcile writes nothing when nothing differs, so this cannot loop.
        viewModelScope.launch {
            combine(accounts, transactions, monthEndTick) { _, _, _ -> }.collectLatest {
                kotlinx.coroutines.delay(300)
                reconcileMonthEnd(db, getApplication())
            }
        }
        viewModelScope.launch { ensureDefaultCategories() }
    }

    private suspend fun ensureDefaultCategories() = seedLock.withLock {
        if (dao.categoryCount() > 0) return@withLock
        val expense = listOf("Groceries", "Rent", "Transport", "Dining", "Utilities", "Health", "Shopping", "Entertainment", "Other")
        val income = listOf("Salary", "Gifts", "Other income")
        dao.insertCategories(
            expense.mapIndexed { i, n -> Category(name = n, isIncome = false, color = CategoryPalette[i % CategoryPalette.size]) } +
                income.mapIndexed { i, n -> Category(name = n, isIncome = true, color = CategoryPalette[(i + 2) % CategoryPalette.size]) }
        )
    }

    fun shiftMonth(by: Long) { month.value = month.value.plusMonths(by) }
    fun clearMessage() { message.value = null }
    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    // Accounts
    fun saveAccount(a: Account) = launch { if (a.id == 0L) dao.insertAccount(a) else dao.updateAccount(a) }
    fun deleteAccount(a: Account) = launch {
        dao.deleteRecurringTransfersTo(a.id) // scheduled transfers into it would have nowhere to go
        dao.deleteAccount(a)
    }

    // Transactions
    fun saveTxn(t: Txn) = launch { if (t.id == 0L) dao.insertTxn(t) else dao.updateTxn(t) }
    fun deleteTxn(t: Txn) = launch { t.transferId?.let { dao.deleteTransfer(it) } ?: dao.deleteTxn(t) }

    fun transfer(from: Long, to: Long, cents: Long, date: Long, note: String) = launch {
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            dao.insertTxn(Txn(accountId = from, categoryId = null, amountCents = -cents, note = note, date = date, transferId = id))
            dao.insertTxn(Txn(accountId = to, categoryId = null, amountCents = cents, note = note, date = date, transferId = id))
        }
    }

    // Scheduled payments
    fun saveRecurring(r: Recurring) = launch { if (r.id == 0L) dao.insertRecurring(r) else dao.updateRecurring(r) }
    fun deleteRecurring(r: Recurring) = launch { dao.deleteRecurring(r) }
    fun setRecurringActive(r: Recurring, active: Boolean) = launch { dao.updateRecurring(r.copy(active = active)) }

    fun processDue() = launch {
        val added = processDueRecurring(db)
        reconcileMonthEnd(db, getApplication())
        val parts = listOfNotNull(
            if (added > 0) "$added scheduled payment${if (added == 1) "" else "s"} added" else null,
        )
        if (parts.isNotEmpty()) message.value = parts.joinToString(", ")
    }

    // Categories and budgets
    fun addCategory(name: String, isIncome: Boolean) = launch {
        val clean = name.trim()
        if (clean.isEmpty() || categories.value.any { it.name.equals(clean, true) }) {
            message.value = "Category name is empty or already used"
        } else {
            dao.insertCategory(Category(name = clean, isIncome = isIncome, color = CategoryPalette[categories.value.size % CategoryPalette.size]))
        }
    }
    fun deleteCategory(c: Category) = launch { dao.deleteCategory(c) }

    fun setBudget(categoryId: Long, cents: Long) = launch {
        if (cents <= 0) dao.deleteBudgetFor(categoryId) else dao.upsertBudget(Budget(categoryId = categoryId, limitCents = cents))
    }

    // CSV
    fun exportCsv(uri: Uri) = launch {
        try {
            val accs = dao.accountsOnce().associate { it.id to it.name }
            val cats = dao.categoriesOnce().associate { it.id to it.name }
            val rows = listOf(Csv.HEADER) + dao.transactionsOnce().filter { !it.isAuto() }.map {
                listOf(it.localDate().toString(), accs[it.accountId].orEmpty(), cats[it.categoryId].orEmpty(),
                    Money.plain(it.amountCents), it.note, it.transferId.orEmpty())
            }
            withContext(Dispatchers.IO) {
                val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open file")
                out.bufferedWriter().use { it.write(Csv.write(rows)) }
            }
            message.value = "Exported ${rows.size - 1} transactions"
        } catch (e: Exception) {
            message.value = "Export failed: ${e.message}"
        }
    }

    fun importCsv(uri: Uri) = launch {
        try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot open file")
            }
            val rows = Csv.parse(text.removePrefix("\uFEFF"))
            require(rows.isNotEmpty() && rows[0].map { it.trim().lowercase() }.take(4) == Csv.HEADER.take(4)) {
                "expected columns: ${Csv.HEADER.joinToString()}"
            }
            val (ok, skipped) = importRows(rows.drop(1))
            message.value = "Imported $ok transactions, skipped $skipped"
        } catch (e: Exception) {
            message.value = "Import failed: ${e.message}"
        }
    }

    private suspend fun importRows(rows: List<List<String>>): Pair<Int, Int> = db.withTransaction {
        val accIds = dao.accountsOnce().associate { it.name to it.id }.toMutableMap()
        val catIds = dao.categoriesOnce().associate { it.name to it.id }.toMutableMap()
        var ok = 0
        var skipped = 0
        for (r in rows) {
            val date = r.getOrNull(0)?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val amount = r.getOrNull(3)?.let { Money.parse(it) }
            val accName = r.getOrNull(1)?.trim().orEmpty()
            if (date == null || amount == null || accName.isEmpty()) { skipped++; continue }
            val transferId = r.getOrNull(5)?.trim()?.ifEmpty { null }
            val catName = r.getOrNull(2)?.trim().orEmpty()
            val accountId = accIds.getOrPut(accName) { dao.insertAccount(Account(name = accName)) }
            val categoryId = if (catName.isEmpty() || transferId != null) null else catIds.getOrPut(catName) {
                dao.insertCategory(Category(name = catName, isIncome = amount > 0, color = CategoryPalette[catIds.size % CategoryPalette.size]))
            }
            dao.insertTxn(Txn(accountId = accountId, categoryId = categoryId, amountCents = amount,
                note = r.getOrNull(4).orEmpty(), date = date.toEpochDay(), transferId = transferId))
            ok++
        }
        ok to skipped
    }

    // Debug helpers: sample data so the UI can be judged with real-looking content.
    fun clearAllData() = launch {
        db.withTransaction { dao.clearBudgets(); dao.clearTransactions(); dao.clearAccounts() }
        message.value = "All accounts, transactions and budgets cleared"
    }

    fun loadDemoData() = launch {
        if (accounts.value.isNotEmpty()) { message.value = "Clear existing data first"; return@launch }
        ensureDefaultCategories() // the startup seeding may not have finished yet
        db.withTransaction {
            val cats = dao.categoriesOnce().associateBy { it.name }
            fun cat(n: String) = cats[n]?.id
            val checking = dao.insertAccount(Account(name = "Checking", initialCents = 250_000))
            val savings = dao.insertAccount(Account(name = "Savings", initialCents = 1_200_000))
            val card = dao.insertAccount(Account(name = "Credit Card", initialCents = -35_000))
            val rnd = kotlin.random.Random(42)
            val today = LocalDate.now()

            for (m in 5 downTo 0L) {
                val first = today.withDayOfMonth(1).minusMonths(m)
                fun day(n: Int): LocalDate = first.plusDays(n - 1L).coerceAtMost(today)
                suspend fun add(acc: Long, category: String, cents: Long, d: LocalDate, note: String) {
                    dao.insertTxn(Txn(accountId = acc, categoryId = cat(category), amountCents = cents, note = note, date = d.toEpochDay()))
                }
                suspend fun spend(acc: Long, category: String, count: Int, lo: Int, hi: Int, notes: List<String>) {
                    repeat(count) { add(acc, category, -rnd.nextInt(lo * 100, hi * 100).toLong(), day(rnd.nextInt(1, 29)), notes.random(rnd)) }
                }
                add(checking, "Salary", 420_000, day(1), "Paycheck")
                if (m % 2L == 0L) add(checking, "Gifts", 6_000, day(14), "Birthday money")
                add(checking, "Rent", -145_000, day(3), "Monthly rent")
                add(checking, "Utilities", -rnd.nextInt(110, 190) * 100L, day(8), "Electric & internet")
                spend(card, "Groceries", 6, 22, 96, listOf("Corner Grocer", "Fresh Foods", "Market"))
                spend(card, "Dining", 4, 12, 62, listOf("Pizza night", "Coffee", "Lunch out", "Sushi"))
                spend(checking, "Transport", 4, 8, 46, listOf("Fuel", "Bus pass", "Ride share"))
                spend(card, "Shopping", rnd.nextInt(1, 3), 25, 160, listOf("Clothes", "Home goods", "Headphones"))
                spend(card, "Entertainment", 2, 10, 42, listOf("Cinema", "Streaming", "Concert"))
                if (rnd.nextBoolean()) spend(checking, "Health", 1, 20, 90, listOf("Pharmacy", "Dentist"))
                // Savings transfer plus paying off part of the card.
                for ((from, to, cents) in listOf(Triple(checking, savings, 50_000L), Triple(checking, card, 60_000L))) {
                    val id = UUID.randomUUID().toString()
                    val d = day(2).toEpochDay()
                    dao.insertTxn(Txn(accountId = from, categoryId = null, amountCents = -cents, note = "Transfer", date = d, transferId = id))
                    dao.insertTxn(Txn(accountId = to, categoryId = null, amountCents = cents, note = "Transfer", date = d, transferId = id))
                }
            }
            listOf(
                Triple("Netflix", "Entertainment", 1599L), Triple("Spotify", "Entertainment", 1099L),
                Triple("Gym", "Health", 2900L),
            ).forEachIndexed { i, (n, c, v) ->
                val next = today.plusDays(3L + i * 4)
                dao.insertRecurring(Recurring(accountId = card, categoryId = cat(c), amountCents = -v, name = n,
                    frequency = Freq.MONTHLY.name, nextDate = next.toEpochDay(), anchorDay = next.dayOfMonth))
            }
            dao.insertRecurring(Recurring(accountId = checking, categoryId = null, amountCents = 20_000, name = "Monthly savings",
                frequency = Freq.MONTHLY.name, nextDate = today.plusDays(5).toEpochDay(), anchorDay = today.plusDays(5).dayOfMonth, toAccountId = savings))
            dao.insertRecurring(Recurring(accountId = checking, categoryId = cat("Utilities"), amountCents = -9900, name = "Insurance",
                frequency = Freq.YEARLY.name, nextDate = today.plusDays(40).toEpochDay(), anchorDay = today.plusDays(40).dayOfMonth))
            mapOf("Groceries" to 450, "Dining" to 200, "Transport" to 150, "Shopping" to 200,
                "Entertainment" to 100, "Rent" to 1450, "Utilities" to 200).forEach { (n, v) ->
                cat(n)?.let { dao.upsertBudget(Budget(categoryId = it, limitCents = v * 100L)) }
            }
        }
        message.value = "Demo data loaded"
    }
}
