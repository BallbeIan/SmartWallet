package app.smartwallet.data

import androidx.room.withTransaction
import java.time.LocalDate

/**
 * Turns every due occurrence into a real transaction, dated when it was due, and moves nextDate forward.
 * Safe to call repeatedly and from several places (the app and the background worker). Returns how many were added.
 */
suspend fun processDueRecurring(db: WalletDb): Int {
    val dao = db.dao()
    val today = LocalDate.now()
    var added = 0
    db.withTransaction {
        for (r in dao.recurringDue(today.toEpochDay())) {
            var next = LocalDate.ofEpochDay(r.nextDate)
            var guard = 0 // a bad row must never loop forever
            while (!next.isAfter(today) && guard++ < 1000) {
                dao.insertTxn(Txn(accountId = r.accountId, categoryId = r.categoryId, amountCents = r.amountCents, note = r.name, date = next.toEpochDay()))
                added++
                next = r.advance(next)
            }
            dao.updateRecurring(r.copy(nextDate = next.toEpochDay()))
        }
    }
    return added
}
