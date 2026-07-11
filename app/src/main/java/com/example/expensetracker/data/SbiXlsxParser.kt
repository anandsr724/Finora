package com.example.expensetracker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

class SbiXlsxParser : StatementParser {

    companion object {
        private const val TAG = "SbiXlsxParser"
    }

    override fun parse(context: Context, uri: Uri): List<ParsedTransaction> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open URI: $uri")
        return stream.use { parseZip(it) }
    }

    private fun parseZip(inputStream: InputStream): List<ParsedTransaction> {
        var sharedStrings: List<String> = emptyList()
        var sheetBytes: ByteArray? = null

        // Single-pass ZIP traversal — read both entries as ByteArrays before SAX parsing
        // (SAX may close the underlying stream; readBytes() copies each entry safely)
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        val bytes = zip.readBytes()
                        sharedStrings = parseSharedStrings(ByteArrayInputStream(bytes))
                    }
                    "xl/worksheets/sheet1.xml" -> {
                        sheetBytes = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return sheetBytes?.let { parseSheet(ByteArrayInputStream(it), sharedStrings) }
            ?: emptyList()
    }

    // ── Shared strings ──────────────────────────────────────────────────────────

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        var inT = false
        val buf = StringBuilder()

        // One entry per <si> element. A single <si> may contain multiple <t> runs
        // (rich text formatting). We must concatenate all <t> within each <si> and
        // add exactly one entry — matching the cell's 0-based string index to <si> index.
        SAXParserFactory.newInstance().newSAXParser().parse(stream, object : DefaultHandler() {
            override fun startElement(u: String, local: String, q: String, a: Attributes) {
                when (localOrQ(local, q)) {
                    "si" -> buf.clear()
                    "t"  -> inT = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT) buf.append(ch, start, length)
            }
            override fun endElement(u: String, local: String, q: String) {
                when (localOrQ(local, q)) {
                    "t"  -> inT = false
                    "si" -> strings.add(buf.toString())
                }
            }
        })

        return strings
    }

    // ── Worksheet ───────────────────────────────────────────────────────────────

    private fun parseSheet(stream: InputStream, sharedStrings: List<String>): List<ParsedTransaction> {
        val result = mutableListOf<ParsedTransaction>()
        val headerFound = BooleanArray(1)  // BooleanArray avoids captured-local mutation issue in SAX handler

        var currentRow = 0
        val rowCells = mutableMapOf<String, String>()
        var currentCellCol = ""
        var currentCellType = ""
        var inV = false
        val vBuf = StringBuilder()

        SAXParserFactory.newInstance().newSAXParser().parse(stream, object : DefaultHandler() {
            override fun startElement(u: String, local: String, q: String, a: Attributes) {
                when (localOrQ(local, q)) {
                    "row" -> {
                        currentRow = a.getValue("r")?.toIntOrNull() ?: 0
                        rowCells.clear()
                    }
                    "c" -> {
                        val ref = a.getValue("r") ?: ""
                        currentCellCol = ref.takeWhile { it.isLetter() }.uppercase()
                        currentCellType = a.getValue("t") ?: ""
                    }
                    "v" -> { inV = true; vBuf.clear() }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inV) vBuf.append(ch, start, length)
            }

            override fun endElement(u: String, local: String, q: String) {
                when (localOrQ(local, q)) {
                    "v" -> {
                        inV = false
                        val raw = vBuf.toString()
                        val resolved = if (currentCellType == "s") {
                            val idx = raw.toIntOrNull() ?: -1
                            if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                        } else raw
                        rowCells[currentCellCol] = resolved
                    }
                    "row" -> processRow(currentRow, rowCells, result, headerFound)
                }
            }
        })

        return result
    }

    private fun processRow(
        rowNum: Int,
        cells: Map<String, String>,
        output: MutableList<ParsedTransaction>,
        headerFound: BooleanArray
    ) {
        if (rowNum < 18) return  // metadata rows

        val dateCell   = cells["A"]?.trim() ?: ""
        val details    = cells["B"]?.trim()?.replace("\n", " ") ?: ""
        val refNo      = cells["C"]?.trim() ?: ""
        val debitCell  = cells["D"]?.trim() ?: ""
        val creditCell = cells["E"]?.trim() ?: ""

        // Row 18 is the header row
        if (rowNum == 18) {
            if (dateCell.equals("Date", ignoreCase = true) ||
                dateCell.equals("Txn Date", ignoreCase = true) ||
                dateCell.isNotEmpty()) {
                headerFound[0] = true
            }
            return
        }

        if (!headerFound[0]) return
        if (dateCell.isEmpty()) return
        if (dateCell.contains("Opening Balance", ignoreCase = true)) return
        if (dateCell.contains("Closing Balance", ignoreCase = true)) return
        if (dateCell.contains("Total", ignoreCase = true)) return

        val dateStr = convertSbiDate(dateCell) ?: return

        // Determine type and amount
        val debitVal  = debitCell.replace(",", "").toDoubleOrNull()
        val creditVal = creditCell.replace(",", "").toDoubleOrNull()

        val (amount, type) = when {
            debitVal != null && debitVal > 0.0  -> debitCell.replace(",", "") to "expense"
            creditVal != null && creditVal > 0.0 -> creditCell.replace(",", "") to "income"
            else -> return
        }

        val recipient = extractRecipient(details)
        val txnId     = extractTransactionId(details, refNo)
        val isUpi     = details.uppercase().let { it.contains("UPI/DR/") || it.contains("UPI/CR/") }

        output.add(ParsedTransaction(
            date          = dateStr,
            recipient     = recipient,
            note          = details,
            amount        = amount,
            transactionId = txnId,
            bankInfo      = if (isUpi) "UPI" else "SBI",
            type          = type,
            category      = CategoryGuesser.guess(recipient + " " + details)
        ))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    fun extractRecipient(details: String): String {
        val upper  = details.uppercase()
        val drIdx  = upper.indexOf("UPI/DR/")
        val crIdx  = upper.indexOf("UPI/CR/")
        val upiIdx = when {
            drIdx >= 0 && crIdx >= 0 -> minOf(drIdx, crIdx)
            drIdx >= 0               -> drIdx
            crIdx >= 0               -> crIdx
            else                     -> -1
        }
        if (upiIdx >= 0) {
            // afterDir = "TXNID/RECIPIENT/BANK/VPA/..."
            val afterDir = details.substring(upiIdx + 7)  // skip "UPI/DR/"
            val parts    = afterDir.split("/")
            val txnId    = parts.getOrNull(0)?.trim() ?: ""
            val raw      = parts.getOrNull(1)?.trim() ?: ""
            // Guard: txnId must look like a numeric UPI transaction ID
            if (txnId.all { it.isDigit() } && txnId.length >= 8) {
                return raw.ifBlank { details.take(40) }
            }
            // Fallback if the path doesn't have the expected shape
            return raw.ifBlank { details.take(40) }
        }
        if (upper.contains("NEFT")) {
            val neftPart = details.substringAfter("NEFT", "")
            val parts    = neftPart.split("*")
            return parts.lastOrNull { it.isNotBlank() }?.trim()?.take(40) ?: details.take(40)
        }
        if (upper.startsWith("INTEREST")) return "SBI Interest"
        return details.split(" ").take(3).joinToString(" ").take(40)
    }

    fun extractTransactionId(details: String, refNo: String): String {
        val upper  = details.uppercase()
        val drIdx  = upper.indexOf("UPI/DR/")
        val crIdx  = upper.indexOf("UPI/CR/")
        val upiIdx = when {
            drIdx >= 0 && crIdx >= 0 -> minOf(drIdx, crIdx)
            drIdx >= 0               -> drIdx
            crIdx >= 0               -> crIdx
            else                     -> -1
        }
        if (upiIdx >= 0) {
            val afterDir  = details.substring(upiIdx + 7)
            val candidate = afterDir.split("/").getOrNull(0)?.trim() ?: ""
            if (candidate.all { it.isDigit() } && candidate.length >= 8) return candidate
        }
        return refNo.ifBlank { "" }
    }

    private fun convertSbiDate(raw: String): String? {
        return try {
            val inFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
            val date = inFmt.parse(raw.trim()) ?: return null
            val cal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(cal.time)
        } catch (e: Exception) {
            Log.w(TAG, "Date parse failed: $raw")
            null
        }
    }

    private fun localOrQ(local: String, q: String) = if (local.isNotEmpty()) local else q
}
