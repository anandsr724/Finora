package com.example.expensetracker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.*

class SbiPdfParser : StatementParser {

    companion object {
        private const val TAG = "SbiPdfParser"
        // Matches DD/MM/YYYY at the very start of a trimmed line
        private val DATE_LINE    = Regex("""^\d{2}/\d{2}/\d{4}""")
        // Second date in same row (SBI prints "Date" + "Value Date" on one line)
        private val SECOND_DATE  = Regex("""^\d{2}/\d{2}/\d{4}\s*""")
        // Indian comma-format number: 1,23,456.78 or 10,650.00 or 315.78
        private val AMOUNT_RE    = Regex("""\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?""")
        // Marker where the amount columns begin in a table row ("   -   DEBIT")
        private val AMOUNT_START = Regex("""\s{2,}-\s""")
    }

    override fun parse(context: Context, uri: Uri): List<ParsedTransaction> {
        PDFBoxResourceLoader.init(context)
        val result = mutableListOf<ParsedTransaction>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val doc  = PDDocument.load(stream)
            // sortByPosition=true produces row-by-row layout (like pdftotext -layout)
            // rather than column-by-column which is the default for generated table PDFs
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val text = stripper.getText(doc)
            doc.close()

            val lines  = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val blocks = groupIntoBlocks(lines)

            var previousBalance: Double? = null
            for (block in blocks) {
                parseBlock(block, previousBalance)?.let { (tx, balance) ->
                    result.add(tx)
                    previousBalance = balance
                }
            }
        }

        Log.d(TAG, "SBI PDF parse complete: ${result.size} transactions")
        return result
    }

    // ── Grouping ──────────────────────────────────────────────────────────────────

    private fun groupIntoBlocks(lines: List<String>): List<List<String>> {
        val blocks  = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null

        for (line in lines) {
            when {
                isSkipLine(line) -> { /* skip headers/footers */ }
                DATE_LINE.containsMatchIn(line) -> {
                    current = mutableListOf(line)
                    blocks.add(current)
                }
                current != null -> current.add(line)
            }
        }
        return blocks
    }

    // ── Block parsing ─────────────────────────────────────────────────────────────

    private fun parseBlock(lines: List<String>, previousBalance: Double?): Pair<ParsedTransaction, Double>? {
        val mainLine = lines[0].trim()
        if (mainLine.length < 10) return null

        // --- Date (first 10 chars) ---
        val datePart = mainLine.substring(0, 10)
        val dateStr  = convertDate(datePart) ?: return null

        // --- Strip both dates ---
        // SBI format: "DD/MM/YYYY      DD/MM/YYYY   UPI/DR/TXNID/RECIPIENT   -   DEBIT   -   BALANCE"
        var lineContent = mainLine.substring(10).trim()
        lineContent     = SECOND_DATE.replaceFirst(lineContent, "").trim()

        // Split into description part and amount-column part at the first "   -  " boundary
        val amountStartMatch = AMOUNT_START.find(lineContent)
        val descPart   = if (amountStartMatch != null)
            lineContent.substring(0, amountStartMatch.range.first).trim()
        else
            lineContent.trim()
        val amountPart = if (amountStartMatch != null)
            lineContent.substring(amountStartMatch.range.first)
        else
            lineContent

        // --- Amounts from main line ONLY ---
        // (Avoids spurious matches from branch-reference numbers in continuation lines)
        val amounts = AMOUNT_RE.findAll(amountPart)
            .map { it.value }
            .filter { it.replace(",", "").toDoubleOrNull() != null }
            .toList()

        if (amounts.isEmpty()) {
            // Fallback: scan the whole line if no amounts found in amount section
            val fallback = AMOUNT_RE.findAll(lineContent)
                .map { it.value }
                .filter { it.replace(",", "").toDoubleOrNull() != null }
                .toList()
            if (fallback.size < 2) return null
            val balance   = fallback.last().replace(",", "").toDoubleOrNull() ?: return null
            val amountRaw = fallback[fallback.size - 2].replace(",", "")
            return buildTransaction(lines, dateStr, descPart, amountRaw, balance, lineContent, previousBalance)
        }

        val balance   = amounts.last().replace(",", "").toDoubleOrNull() ?: return null
        val amountRaw = if (amounts.size >= 2) amounts[amounts.size - 2].replace(",", "") else balance.toString()

        return buildTransaction(lines, dateStr, descPart, amountRaw, balance, lineContent, previousBalance)
    }

    private fun buildTransaction(
        lines: List<String>,
        dateStr: String,
        descPart: String,
        amountRaw: String,
        balance: Double,
        lineContent: String,
        previousBalance: Double?
    ): Pair<ParsedTransaction, Double>? {
        if (amountRaw.toDoubleOrNull() == null) return null

        // --- Continuation lines for UPI path remainder ---
        // After the main line, continuation lines may have:
        //   "/BANK/vpa/UPI"         → bank code only, no recipient continuation
        //   "NAMEPART/BANK/vpa/UPI" → NAMEPART is end of recipient name, then bank code
        //   "digits AT branch"      → branch reference (skip)
        //   "WDL TFR" etc.          → prefix of next transaction (skip)
        val nameContinuation = findNameContinuation(lines.drop(1))

        val (recipient, txnId) = extractUpiInfo(descPart, nameContinuation)

        val isUpi     = lineContent.uppercase().let { it.contains("UPI/DR/") || it.contains("UPI/CR/") }
        val type      = classifyTransaction(lineContent, balance, previousBalance)
        val bankInfo  = if (isUpi) "UPI" else "SBI"
        // Strip amount columns from note; they are stored separately as `amountRaw`
        val cleanDesc = descPart.split(Regex(""" - |\s{2,}""")).firstOrNull()?.trim() ?: descPart
        val note      = if (nameContinuation.isNotEmpty()) "$cleanDesc $nameContinuation" else cleanDesc

        return ParsedTransaction(
            date          = dateStr,
            recipient     = recipient,
            note          = note,
            amount        = amountRaw,
            transactionId = txnId,
            bankInfo      = bankInfo,
            type          = type,
            category      = CategoryGuesser.guess("$recipient $note")
        ) to balance
    }

    // ── Continuation-line analysis ────────────────────────────────────────────────

    /**
     * Scans lines after the main transaction line for the tail of the recipient name.
     *
     * SBI PDF puts a fixed-width table on each row. When the recipient name is long it
     * wraps to the next line together with the bank-code and VPA:
     *
     *   Main:  "UPI/DR/TXNID/NAVEEN   -   10,650.00   -   4,05,822.92"
     *   Next:  "/SBIN/jain26nave/UPI"      → starts with "/" → NO extra name
     *
     *   Main:  "UPI/DR/TXNID/Sadula   -   50.00   -   4,05,772.92"
     *   Next:  "G/YESB/paytm.s1tx/UPI"    → 2nd segment is 4-letter bank code → "G" is name tail
     *
     *   Main:  "UPI/DR/TXNID/Zepto/YES   -   348.00   -   3,08,412.58"
     *   Next:  "B/ZEPTOONLIN/NO REMA"      → 2nd segment NOT 4 letters → "B" is bank tail, no name
     */
    private fun findNameContinuation(lines: List<String>): String {
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("/")) continue          // pure bank segment, no name
            if (t.matches(Regex("""\d+\s+AT\s+.*"""))) break  // branch-ref line → stop
            // Location / next-transaction lines → stop
            if (!t.contains("/")) break

            val slashIdx  = t.indexOf('/')
            val firstSeg  = t.substring(0, slashIdx)
            val remainder = t.substring(slashIdx + 1)
            val secondSeg = remainder.substringBefore("/")

            // If 2nd segment is a 4-letter all-uppercase bank code → 1st segment is name tail
            if (secondSeg.length == 4 && secondSeg.all { it.isUpperCase() }) {
                return firstSeg
            }
            break  // anything else (e.g. bank-code split like "B/ZEPTOONLIN") → no name
        }
        return ""
    }

    // ── UPI info extraction ───────────────────────────────────────────────────────

    private fun extractUpiInfo(descPart: String, nameContinuation: String): Pair<String, String> {
        val upper  = descPart.uppercase()
        val drIdx  = upper.indexOf("UPI/DR/")
        val crIdx  = upper.indexOf("UPI/CR/")
        val upiIdx = when {
            drIdx >= 0 && crIdx >= 0 -> minOf(drIdx, crIdx)
            drIdx >= 0               -> drIdx
            crIdx >= 0               -> crIdx
            else                     -> -1
        }

        if (upiIdx >= 0) {
            // afterDir = "TXNID/RECIPIENT_FIRST[/BANK/...]"
            val afterDir       = descPart.substring(upiIdx + 7)  // skip "UPI/DR/"
            val parts          = afterDir.split("/")
            val txnId          = parts.getOrNull(0)?.trim()
                ?.let { if (it.all { c -> c.isDigit() } && it.length >= 8) it else "" } ?: ""
            // Recipient first part — strip amounts that leak in when PdfBox uses single-space
            // column separators. The Ref No column is always a lone "-" so " - " is the reliable
            // boundary between the description and the amount columns.
            val recipientFirst = (parts.getOrNull(1)?.trim() ?: "")
                .split(Regex(""" - |\s{2,}""")).firstOrNull()?.trim() ?: ""

            val recipient = buildString {
                append(recipientFirst)
                if (nameContinuation.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append(nameContinuation)
                }
            }.take(50).ifBlank { "Unknown" }

            return recipient to txnId
        }

        // NEFT: "NEFT*BANKCODE*REF*RECIPIENT" — last *-segment may have amounts appended
        if (upper.contains("NEFT")) {
            val parts   = descPart.substringAfter("NEFT").split("*")
            val raw     = parts.lastOrNull { it.isNotBlank() }?.trim() ?: descPart.take(40)
            val recipient = raw.split(Regex(""" - |\s{2,}""")).firstOrNull()?.trim()?.take(40) ?: raw.take(40)
            return recipient to ""
        }

        return descPart.split(" ").take(3).joinToString(" ").take(40) to ""
    }

    // ── Classification ────────────────────────────────────────────────────────────

    private fun classifyTransaction(lineContent: String, currentBalance: Double, previousBalance: Double?): String {
        val upper = lineContent.uppercase()
        return when {
            upper.contains("UPI/DR/") || upper.contains("UPI-DR-") -> "expense"
            upper.contains("UPI/CR/") || upper.contains("UPI-CR-") -> "income"
            upper.startsWith("WDL") || upper.startsWith("ATM") ||
            upper.startsWith("DEBIT") || upper.contains(" DR ") -> "expense"
            upper.startsWith("DEP") || upper.startsWith("SALARY") ||
            upper.startsWith("INT ") || upper.startsWith("INTEREST") ||
            upper.contains(" CR ") || upper.contains("CEMTEX") -> "income"
            previousBalance != null -> if (currentBalance < previousBalance) "expense" else "income"
            else -> "expense"
        }
    }

    // ── Skip-line filter ──────────────────────────────────────────────────────────

    private fun isSkipLine(line: String): Boolean {
        val upper = line.uppercase()
        return (upper.contains("DATE") && upper.contains("DESCRIPTION")) ||
               upper.contains("OPENING BALANCE") ||
               upper.contains("CLOSING BALANCE") ||
               upper.startsWith("PAGE ") ||
               upper.contains("GENERATED ON") ||
               upper.contains("ACCOUNT STATEMENT") ||
               upper.contains("STATEMENT FROM") ||
               upper.contains("ACCOUNT SUMMARY") ||
               upper.contains("BRANCH CODE") ||
               upper.contains("BROUGHT FORWARD") ||
               upper.contains("TOTAL DEBIT") ||
               upper.contains("TOTAL CREDIT") ||
               (upper.contains("REF NO") && upper.contains("CHEQUE")) ||
               (upper.contains("BALANCE") && !DATE_LINE.containsMatchIn(line))
    }

    // ── Date conversion ───────────────────────────────────────────────────────────

    private fun convertDate(raw: String): String? {
        return try {
            val inFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
            val date  = inFmt.parse(raw.trim()) ?: return null
            val cal   = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(cal.time)
        } catch (e: Exception) {
            null
        }
    }
}
