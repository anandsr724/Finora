package com.example.expensetracker.data

import com.example.expensetracker.CurrencyManager
import com.example.expensetracker.PaymentTransaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Central duplicate-detection logic shared by every transaction entry point (manual entry,
 * OCR receipt scan, and bank-statement import) so a transaction recorded twice by different
 * routes is always caught, not just re-imports of the exact same statement.
 */
object DuplicateDetector {

    enum class MatchReason { TRANSACTION_ID, AMOUNT_DATE }

    data class Match(val existing: PaymentTransaction, val reason: MatchReason)

    private fun dateFormat() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    private fun sameDay(a: String, b: String): Boolean {
        return try {
            val fmt = dateFormat()
            val da = fmt.parse(a) ?: return false
            val db = fmt.parse(b) ?: return false
            val ca = Calendar.getInstance().apply { time = da }
            val cb = Calendar.getInstance().apply { time = db }
            ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Finds existing transactions that look like the same real-world payment as the given
     * candidate fields. Two signals are used, either of which is enough to flag a match:
     *  - the same non-empty transaction/reference ID (e.g. a bank UTR or UPI transaction ID)
     *  - the same amount, currency and type (expense/income) on the same calendar day — this
     *    is what catches a manually-entered transaction later re-appearing in a statement
     *    import, since a manual entry never has a bank transaction ID to match against.
     */
    fun findMatches(
        amount: String,
        currency: String,
        type: String,
        dateTime: String,
        transactionId: String,
        existing: List<PaymentTransaction>,
        excludeId: String? = null
    ): List<Match> {
        val candidateAmount = CurrencyManager.parseAmount(amount)
        val trimmedTxId = transactionId.trim()
        val pool = if (excludeId != null) existing.filter { it.id != excludeId } else existing

        val results = mutableListOf<Match>()
        for (tx in pool) {
            if (trimmedTxId.isNotEmpty() && tx.transactionId.trim().equals(trimmedTxId, ignoreCase = true)) {
                results.add(Match(tx, MatchReason.TRANSACTION_ID))
                continue
            }
            if (tx.type != type || !tx.currency.equals(currency, ignoreCase = true)) continue
            val existingAmount = CurrencyManager.parseAmount(tx.amount)
            if (abs(existingAmount - candidateAmount) > 0.01) continue
            if (!sameDay(tx.dateTime, dateTime)) continue
            results.add(Match(tx, MatchReason.AMOUNT_DATE))
        }
        return results
    }

    fun findMatches(candidate: PaymentTransaction, existing: List<PaymentTransaction>): List<Match> =
        findMatches(
            amount = candidate.amount,
            currency = candidate.currency,
            type = candidate.type,
            dateTime = candidate.dateTime,
            transactionId = candidate.transactionId,
            existing = existing,
            excludeId = candidate.id
        )
}
