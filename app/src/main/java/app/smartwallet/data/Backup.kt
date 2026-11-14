package app.smartwallet.data

import androidx.room.withTransaction
import app.smartwallet.CategoryPalette
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private const val FORMAT = 1

/** Which parts of the app's data go into an export, or come out of an import. */
data class Parts(
    val accounts: Boolean = true, val categories: Boolean = true, val transactions: Boolean = true,
    val budgets: Boolean = true, val scheduled: Boolean = true, val settings: Boolean = true,
) {
    val any get() = accounts || categories || transactions || budgets || scheduled || settings
}

/** How many of each kind of thing a file holds, or an import added. */
data class Counts(
    val accounts: Int = 0, val categories: Int = 0, val transactions: Int = 0, val budgets: Int = 0, val scheduled: Int = 0,
    val alreadyThere: Int = 0,
)

/** A parsed backup file, kept as JSON so older and newer files are read the same way. */
class BackupInfo(val json: JSONObject) {
    fun has(section: String) = json.optJSONArray(section) != null
    fun count(section: String) = json.optJSONArray(section)?.length() ?: 0
    val settings: JSONObject? get() = json.optJSONObject("settings")
    val counts get() = Counts(count("accounts"), count("categories"), count("transactions"), count("budgets"), count("scheduled"))
}

fun parseBackup(text: String): BackupInfo {
    val o = try { JSONObject(text.removePrefix("\uFEFF")) } catch (e: org.json.JSONException) { throw IllegalArgumentException("this is not a SmartWallet backup file") }
    require(o.optString("app") == "SmartWallet") { "this is not a SmartWallet backup file" }
    require(o.optInt("format", 0) in 1..FORMAT) { "this backup was made by a newer version of SmartWallet" }
    return BackupInfo(o)
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun List<Any?>.toJsonArray() = JSONArray().also { a -> forEach { a.put(it) } }

/**
 * Builds the backup file. Things refer to each other by name (a transaction names its account), so the file can be
 * imported into a fresh install where the database ids are different. Month-end entries are not saved: they are
 * recalculated from the real transactions.
 */
suspend fun buildBackup(dao: WalletDao, parts: Parts, settings: JSONObject?, appVersion: String): String {
    val accounts = dao.accountsOnce()
    val accName = accounts.associate { it.id to it.name }
    val categories = dao.categoriesOnce()
    val catName = categories.associate { it.id to it.name }
    val o = JSONObject().put("app", "SmartWallet").put("format", FORMAT).put("appVersion", appVersion).put("exported", LocalDate.now().toString())

    if (parts.accounts) o.put("accounts", accounts.map { JSONObject().put("name", it.name).put("startingBalanceCents", it.initialCents) }.toJsonArray())
    if (parts.categories) o.put("categories", categories.map { JSONObject().put("name", it.name).put("income", it.isIncome).put("color", it.color) }.toJsonArray())
    if (parts.transactions) o.put("transactions", dao.transactionsOnce().filter { !it.isAuto() }.map {
        JSONObject().put("date", it.localDate().toString()).put("account", accName[it.accountId] ?: "")
            .put("category", catName[it.categoryId] ?: JSONObject.NULL).put("amountCents", it.amountCents)
            .put("note", it.note).put("transferId", it.transferId ?: JSONObject.NULL)
    }.toJsonArray())
    if (parts.budgets) o.put("budgets", dao.budgetsOnce().mapNotNull { b ->
        catName[b.categoryId]?.let { JSONObject().put("category", it).put("limitCents", b.limitCents) }
    }.toJsonArray())
    if (parts.scheduled) o.put("scheduled", dao.recurringOnce().map {
        JSONObject().put("name", it.name).put("account", accName[it.accountId] ?: "").put("toAccount", accName[it.toAccountId] ?: JSONObject.NULL)
            .put("category", catName[it.categoryId] ?: JSONObject.NULL).put("amountCents", it.amountCents).put("frequency", it.frequency)
            .put("nextDate", LocalDate.ofEpochDay(it.nextDate).toString()).put("anchorDay", it.anchorDay).put("active", it.active)
    }.toJsonArray())
    if (parts.settings && settings != null) o.put("settings", settings)
    return o.toString(2)
}

/**
 * Adds the chosen parts of a backup to what the app already has. Nothing is overwritten: accounts, categories and budgets
 * that exist stay as they are, and a transaction or scheduled payment that is already there is skipped, so importing the
 * same file twice changes nothing.
 */
suspend fun importSections(db: WalletDb, info: BackupInfo, parts: Parts): Counts = db.withTransaction {
    val dao = db.dao()
    val accIds = dao.accountsOnce().associate { it.name to it.id }.toMutableMap()
    val catIds = dao.categoriesOnce().associate { it.name to it.id }.toMutableMap()
    var accountsAdded = 0; var categoriesAdded = 0; var txAdded = 0; var budgetsSet = 0; var scheduledAdded = 0; var already = 0

    suspend fun account(name: String): Long = accIds.getOrPut(name) { accountsAdded++; dao.insertAccount(Account(name = name)) }
    suspend fun category(name: String, income: Boolean): Long = catIds.getOrPut(name) {
        categoriesAdded++
        dao.insertCategory(Category(name = name, isIncome = income, color = CategoryPalette[catIds.size % CategoryPalette.size]))
    }

    if (parts.accounts) for (a in info.json.optJSONArray("accounts")?.objects().orEmpty()) {
        val name = a.str("name") ?: continue
        if (name !in accIds) { accIds[name] = dao.insertAccount(Account(name = name, initialCents = a.optLong("startingBalanceCents"))); accountsAdded++ }
    }
    if (parts.categories) for (c in info.json.optJSONArray("categories")?.objects().orEmpty()) {
        val name = c.str("name") ?: continue
        if (name !in catIds) {
            val color = if (c.has("color")) c.optLong("color") else CategoryPalette[catIds.size % CategoryPalette.size]
            catIds[name] = dao.insertCategory(Category(name = name, isIncome = c.optBoolean("income"), color = color)); categoriesAdded++
        }
    }
    if (parts.transactions) {
        // Count what is already stored per (date, account, category, amount, note) so a repeated import skips exactly those.
        data class Key(val date: Long, val account: Long, val category: Long?, val cents: Long, val note: String)
        val have = dao.transactionsOnce().filter { !it.isAuto() }.groupingBy { Key(it.date, it.accountId, it.categoryId, it.amountCents, it.note) }.eachCount().toMutableMap()
        val newTransferIds = mutableMapOf<String, String>()
        for (t in info.json.optJSONArray("transactions")?.objects().orEmpty()) {
            val date = t.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val accName = t.str("account") ?: continue
            val cents = t.optLong("amountCents")
            val transferId = t.str("transferId")
            val accountId = account(accName)
            val categoryId = if (transferId != null) null else t.str("category")?.let { category(it, cents > 0) }
            val note = t.optString("note")
            val key = Key(date.toEpochDay(), accountId, categoryId, cents, note)
            if ((have[key] ?: 0) > 0) { have[key] = have.getValue(key) - 1; already++; continue }
            dao.insertTxn(Txn(accountId = accountId, categoryId = categoryId, amountCents = cents, note = note, date = date.toEpochDay(),
                transferId = transferId?.let { newTransferIds.getOrPut(it) { UUID.randomUUID().toString() } }))
            txAdded++
        }
    }
    if (parts.budgets) for (b in info.json.optJSONArray("budgets")?.objects().orEmpty()) {
        val id = b.str("category")?.let { category(it, false) } ?: continue
        dao.upsertBudget(Budget(categoryId = id, limitCents = b.optLong("limitCents"))); budgetsSet++
    }
    if (parts.scheduled) {
        val existing = dao.recurringOnce().map { listOf(it.name, it.accountId, it.toAccountId, it.amountCents, it.frequency) }.toMutableSet()
        for (r in info.json.optJSONArray("scheduled")?.objects().orEmpty()) {
            val name = r.str("name") ?: continue
            val accName = r.str("account") ?: continue
            val freq = Freq.entries.firstOrNull { it.name == r.optString("frequency") } ?: continue
            val next = r.str("nextDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val accountId = account(accName)
            val toId = r.str("toAccount")?.let { account(it) }
            val cents = r.optLong("amountCents")
            if (!existing.add(listOf(name, accountId, toId, cents, freq.name))) { already++; continue }
            dao.insertRecurring(Recurring(accountId = accountId, categoryId = r.str("category")?.let { catIds[it] }, amountCents = cents, name = name,
                frequency = freq.name, nextDate = next.toEpochDay(), anchorDay = r.optInt("anchorDay", next.dayOfMonth), active = r.optBoolean("active", true), toAccountId = toId))
            scheduledAdded++
        }
    }
    Counts(accountsAdded, categoriesAdded, txAdded, budgetsSet, scheduledAdded, already)
}
