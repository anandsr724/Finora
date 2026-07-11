package com.example.expensetracker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.*

class GpayPdfParser : StatementParser {

    companion object {
        private const val TAG = "GpayPdfParser"
        // "01 Jun, 2026" or "1 Jun, 2026"
        private val DATE_RE   = Regex("""^\d{1,2} \w{3}, \d{4}$""")
        // "10:30 AM" or "2:05 PM"
        private val TIME_RE   = Regex("""^\d{1,2}:\d{2} [AP]M$""")
        // "₹315.78" or "₹32,355"
        private val AMOUNT_RE = Regex("""^₹[\d,]+(\.\d+)?$""")
    }

    private enum class State {
        IDLE, HAVE_DATE, HAVE_TIME, HAVE_DIRECTION, HAVE_TXNID, HAVE_PAYBY
    }

    override fun parse(context: Context, uri: Uri): List<ParsedTransaction> {
        PDFBoxResourceLoader.init(context)
        val result = mutableListOf<ParsedTransaction>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val doc  = PDDocument.load(stream)
            val text = PDFTextStripper().getText(doc)
            doc.close()

            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            var state    = State.IDLE
            var date     = ""
            var time     = ""
            var type     = ""
            var merchant = ""
            var txnId    = ""
            var bankInfo = ""

            fun reset() {
                state = State.IDLE; date = ""; time = ""; type = ""; merchant = ""; txnId = ""; bankInfo = ""
            }

            for (line in lines) {
                when (state) {
                    State.IDLE -> {
                        if (DATE_RE.matches(line)) { date = line; state = State.HAVE_DATE }
                    }
                    State.HAVE_DATE -> {
                        if (TIME_RE.matches(line)) { time = line; state = State.HAVE_TIME }
                        else if (DATE_RE.matches(line)) { date = line }  // consecutive dates — stay in HAVE_DATE
                        else reset()
                    }
                    State.HAVE_TIME -> {
                        when {
                            line.startsWith("Paid to ") -> {
                                type = "expense"
                                merchant = line.removePrefix("Paid to ").trim()
                                state = State.HAVE_DIRECTION
                            }
                            line.startsWith("Received from ") -> {
                                type = "income"
                                merchant = line.removePrefix("Received from ").trim()
                                state = State.HAVE_DIRECTION
                            }
                            else -> reset()
                        }
                    }
                    State.HAVE_DIRECTION -> {
                        txnId = when {
                            line.startsWith("UPI Transaction ID:") ->
                                line.substringAfter("UPI Transaction ID:").trim()
                            line.startsWith("Transaction ID:") ->
                                line.substringAfter("Transaction ID:").trim()
                            else -> { reset(); continue }
                        }
                        state = State.HAVE_TXNID
                    }
                    State.HAVE_TXNID -> {
                        if (line.startsWith("Paid by ")) {
                            bankInfo = line.removePrefix("Paid by ").trim()
                            state = State.HAVE_PAYBY
                        } else {
                            reset()
                        }
                    }
                    State.HAVE_PAYBY -> {
                        if (AMOUNT_RE.matches(line)) {
                            val amount   = line.removePrefix("₹").replace(",", "")
                            val dateTime = buildDateTime(date, time)
                            result.add(ParsedTransaction(
                                date          = dateTime,
                                recipient     = merchant,
                                note          = "",
                                amount        = amount,
                                transactionId = txnId,
                                bankInfo      = bankInfo,
                                type          = type,
                                category      = CategoryGuesser.guess(merchant)
                            ))
                        }
                        reset()
                    }
                }
            }
        }

        Log.d(TAG, "GPay parse complete: ${result.size} transactions")
        return result
    }

    // date = "01 Jun, 2026", time = "08:21 PM" → "01 Jun 2026, 08:21 PM"
    private fun buildDateTime(date: String, time: String): String {
        return try {
            val inFmt  = SimpleDateFormat("dd MMM, yyyy hh:mm a", Locale.ENGLISH)
            val outFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            outFmt.format(inFmt.parse("$date $time")!!)
        } catch (e: Exception) {
            Log.w(TAG, "Date/time parse failed: '$date $time'")
            "$date, $time"
        }
    }
}
