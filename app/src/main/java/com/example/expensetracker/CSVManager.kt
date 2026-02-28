package com.example.expensetracker

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build

data class PaymentTransaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: String,
    val recipient: String,
    val note: String,
    val dateTime: String,
    val transactionId: String,
    val bankInfo: String,
    val category: String = "cat_other",
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val currency: String = "INR"
) {
    // Convert to CSV row
    fun toCsvRow(): String {
        return listOf(id, amount, recipient, note, dateTime, transactionId, bankInfo, category, createdAt, currency)
            .joinToString(",") { escapeCsvField(it) }
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    companion object {
        fun fromCsvRow(csvRow: String): PaymentTransaction? {
            return try {
                val fields = parseCsvRow(csvRow)
                if (fields.size >= 8) {
                    // Normalize amount: strip any stale ₹ symbol and commas from old data
                    val rawAmount = fields[1].replace("₹", "").replace(",", "").trim()
                    PaymentTransaction(
                        id = fields[0],
                        amount = rawAmount,
                        recipient = fields[2],
                        note = fields[3],
                        dateTime = fields[4],
                        transactionId = fields[5],
                        bankInfo = fields[6],
                        category = if (fields.size > 7) fields[7] else "cat_other",
                        createdAt = if (fields.size > 8) fields[8] else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        currency = if (fields.size > 9) fields[9] else "INR"
                    )
                } else null
            } catch (e: Exception) {
                Log.e("PaymentTransaction", "Error parsing CSV row: $csvRow", e)
                null
            }
        }

        private fun parseCsvRow(csvRow: String): List<String> {
            val result = mutableListOf<String>()
            val chars = csvRow.toCharArray()
            var i = 0

            while (i < chars.size) {
                val field = StringBuilder()

                if (chars[i] == '"') {
                    // Quoted field
                    i++ // Skip opening quote
                    while (i < chars.size) {
                        if (chars[i] == '"') {
                            if (i + 1 < chars.size && chars[i + 1] == '"') {
                                // Escaped quote
                                field.append('"')
                                i += 2
                            } else {
                                // End of quoted field
                                i++ // Skip closing quote
                                break
                            }
                        } else {
                            field.append(chars[i])
                            i++
                        }
                    }
                } else {
                    // Unquoted field
                    while (i < chars.size && chars[i] != ',') {
                        field.append(chars[i])
                        i++
                    }
                }

                result.add(field.toString())

                // Skip comma
                if (i < chars.size && chars[i] == ',') {
                    i++
                }
            }

            return result
        }

        fun getCsvHeader(): String {
            return "ID,Amount,Recipient,Note,DateTime,TransactionID,BankInfo,Category,CreatedAt,Currency"
        }
    }
}

class CSVManager(private val context: Context) {

    companion object {
        private const val CSV_FILENAME = "expense_transactions.csv"
        private const val TAG = "CSVManager"
    }

    private val csvFile: File
        get() = File(context.filesDir, CSV_FILENAME)

    // Save a new transaction
    fun saveTransaction(transaction: PaymentTransaction): Boolean {
        return try {
            val fileExists = csvFile.exists()

            FileWriter(csvFile, true).use { writer ->
                // Add header if file is new
                if (!fileExists) {
                    writer.append(PaymentTransaction.getCsvHeader())
                    writer.append("\n")
                }

                // Add transaction data
                writer.append(transaction.toCsvRow())
                writer.append("\n")
                writer.flush()
            }

            Log.d(TAG, "Transaction saved successfully: ${transaction.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transaction", e)
            false
        }
    }

    // Read all transactions
    fun getAllTransactions(): List<PaymentTransaction> {
        val transactions = mutableListOf<PaymentTransaction>()

        if (!csvFile.exists()) {
            return transactions
        }

        try {
            csvFile.bufferedReader().use { reader ->
                var lineNumber = 0
                reader.forEachLine { line ->
                    lineNumber++

                    // Skip header row
                    if (lineNumber == 1) return@forEachLine

                    // Skip empty lines
                    if (line.trim().isEmpty()) return@forEachLine

                    val transaction = PaymentTransaction.fromCsvRow(line)
                    if (transaction != null) {
                        transactions.add(transaction)
                    } else {
                        Log.w(TAG, "Failed to parse line $lineNumber: $line")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading transactions", e)
        }

        return transactions.sortedByDescending { it.createdAt }
    }

    // Get transactions count
    fun getTransactionCount(): Int {
        return getAllTransactions().size
    }

    // Export CSV to Downloads folder - Main method
    fun exportToDownloads(): File? {
        return try {
            if (!csvFile.exists()) {
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore (no permission needed)
                exportUsingMediaStore()
            } else {
                // Android 9 and below - Use traditional file copy
                exportUsingLegacyMethod()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting CSV", e)
            null
        }
    }

    // Modern method for Android 10+
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportUsingMediaStore(): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayName = "ExpenseTracker_$timestamp.csv"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                csvFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Return a File object for consistency (though it's not directly accessible)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            Log.d(TAG, "CSV exported via MediaStore: $displayName")
            return File(downloadsDir, displayName)
        }

        return null
    }

    // Legacy method for Android 9 and below
    private fun exportUsingLegacyMethod(): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportFile = File(downloadsDir, "ExpenseTracker_$timestamp.csv")

        csvFile.copyTo(exportFile, overwrite = true)

        Log.d(TAG, "CSV exported to: ${exportFile.absolutePath}")
        return exportFile
    }

    // Clear all transactions (for testing/reset)
    fun clearAllTransactions(): Boolean {
        return try {
            if (csvFile.exists()) {
                csvFile.delete()
            }
            Log.d(TAG, "All transactions cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing transactions", e)
            false
        }
    }

    // Get CSV file info
    fun getCsvFileInfo(): String {
        return if (csvFile.exists()) {
            val size = csvFile.length()
            val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(csvFile.lastModified()))
            "File: ${csvFile.name}\nSize: ${size} bytes\nLast Modified: $lastModified\nTransactions: ${getTransactionCount()}"
        } else {
            "No transactions saved yet"
        }
    }

    // Update an existing transaction
    fun updateTransaction(updatedTransaction: PaymentTransaction): Boolean {
        return try {
            val allTransactions = getAllTransactions().toMutableList()
            val index = allTransactions.indexOfFirst { it.id == updatedTransaction.id }

            if (index != -1) {
                allTransactions[index] = updatedTransaction
                rewriteCSV(allTransactions)
                Log.d(TAG, "Transaction updated: ${updatedTransaction.id}")
                true
            } else {
                Log.w(TAG, "Transaction not found for update: ${updatedTransaction.id}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating transaction", e)
            false
        }
    }

    // Delete a transaction by ID
    fun deleteTransaction(transactionId: String): Boolean {
        return try {
            val allTransactions = getAllTransactions().toMutableList()
            val removed = allTransactions.removeIf { it.id == transactionId }

            if (removed) {
                rewriteCSV(allTransactions)
                Log.d(TAG, "Transaction deleted: $transactionId")
                true
            } else {
                Log.w(TAG, "Transaction not found for deletion: $transactionId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction", e)
            false
        }
    }

    // Helper method to rewrite the entire CSV file
    private fun rewriteCSV(transactions: List<PaymentTransaction>) {
        FileWriter(csvFile, false).use { writer ->
            // Write header
            writer.append(PaymentTransaction.getCsvHeader())
            writer.append("\n")

            // Write all transactions
            transactions.forEach { transaction ->
                writer.append(transaction.toCsvRow())
                writer.append("\n")
            }

            writer.flush()
        }
    }

}