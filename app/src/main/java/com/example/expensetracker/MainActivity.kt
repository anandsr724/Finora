package com.example.expensetracker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import android.widget.LinearLayout
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log
class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var processButton: Button
    private var currentBitmap: Bitmap? = null
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager  // ADD THIS

    companion object {
        private const val EDIT_REQUEST_CODE = 1001
        private const val STORAGE_PERMISSION_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize CSV manager
        csvManager = CSVManager(this)
        categoryManager = CategoryManager(this)  // ADD THIS
        categoryManager.initializeDefaultCategories()  // ADD THIS

        imageView = findViewById(R.id.imageView)
        statusText = findViewById(R.id.statusText)
        processButton = findViewById(R.id.processButton)

        handleSharedImage()

        processButton.setOnClickListener {
            performMLKitOCR()
        }

        // Add export functionality on long press
        processButton.setOnLongClickListener {
            showExportOptions()
            true
        }
    }

    private fun handleSharedImage() {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (imageUri != null) {
                imageView.setImageURI(imageUri)
                statusText.text = "Image received! Ready to process."
                processButton.isEnabled = true
                convertToBitmap(imageUri)
            }
        }
    }

    private fun convertToBitmap(uri: Uri) {
        try {
            currentBitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            statusText.text = "Image loaded! Size: ${currentBitmap?.width}x${currentBitmap?.height}"
        } catch (e: Exception) {
            statusText.text = "Error loading image: ${e.message}"
        }
    }

    private fun performMLKitOCR() {
        val bitmap = currentBitmap

        if (bitmap == null) {
            statusText.text = "No image to process"
            return
        }

        statusText.text = "Processing image with ML Kit Text Recognition..."
        processButton.isEnabled = false

        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    processButton.isEnabled = true

                    if (visionText.text.isNotBlank()) {
                        statusText.text = "SUCCESS! Processing extracted text..."

                        // Get detailed text blocks and elements
                        val detailedText = buildDetailedText(visionText)
                        parsePaymentInfo(visionText.text, detailedText)
                    } else {
                        statusText.text = "No text found in the image"
                    }
                }
                .addOnFailureListener { e ->
                    processButton.isEnabled = true
                    statusText.text = "ML Kit OCR failed: ${e.message}"
                }

        } catch (e: IOException) {
            processButton.isEnabled = true
            statusText.text = "Error processing image: ${e.message}"
        }
    }

    private fun buildDetailedText(visionText: com.google.mlkit.vision.text.Text): String {
        val detailedBuilder = StringBuilder()

        detailedBuilder.appendLine("=== ML KIT DETAILED ANALYSIS ===")

        for (block in visionText.textBlocks) {
            detailedBuilder.appendLine("BLOCK: ${block.text}")
            detailedBuilder.appendLine("Block Bounds: ${block.boundingBox}")

            for (line in block.lines) {
                detailedBuilder.appendLine("  LINE: ${line.text}")
                detailedBuilder.appendLine("  Line Bounds: ${line.boundingBox}")

                for (element in line.elements) {
                    detailedBuilder.appendLine("    ELEMENT: ${element.text}")
                    detailedBuilder.appendLine("    Element Bounds: ${element.boundingBox}")
                }
            }
            detailedBuilder.appendLine()
        }

        return detailedBuilder.toString()
    }

    private fun parsePaymentInfo(rawText: String, detailedText: String) {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var amount = ""
        var recipient = ""
        var note = ""
        var transactionId = ""
        var dateTime = ""
        var bankInfo = ""

        // Enhanced amount patterns - more flexible
        val amountPatterns = listOf(
            Regex("""₹\s*([0-9,]+(?:\.[0-9]{1,2})?)"""), // ₹500, ₹1,234.56, ₹30.00
            Regex("""Rs\.?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""INR\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*₹"""), // 500 ₹
            Regex("""^([0-9]+(?:\.[0-9]{1,2})?)$"""), // Standalone numbers like "30.00", "1", "500"
            Regex("""Amount.*?₹\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), // ICICI format
            Regex("""Amount.*?([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE) // Generic amount after "Amount"
        )

        // Enhanced recipient patterns
        val recipientPatterns = listOf(
            // Direct "To" patterns
            Regex("""(?:To|TO)\s+([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)+)"""), // "To Anand Sharma"
            Regex("""(?:Paid to|Sent to)\s+([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)+)"""),
            // Name detection after specific labels
            Regex("""Banking Name\s*:\s*([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)+)"""),
            // Generic proper name patterns (2+ words, title case)
            Regex("""^([A-Z][a-z]+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)$""") // Standalone names like "Anand Sharma"
        )

        // More flexible date/time patterns
        val dateTimePatterns = listOf(
            // Google Pay: "13 Sept 2025, 2:40 am"
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{1,2}:\d{2}\s*[ap]m)""", RegexOption.IGNORE_CASE),
            // PhonePe: "07:32 PM on 13 Sep 2025"
            Regex("""(\d{1,2}:\d{2}\s*[AP]M\s+on\s+\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})""", RegexOption.IGNORE_CASE),
            // ICICI: "13 Sep 2025, 08:55 PM"
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // Generic patterns
            Regex("""(\d{1,2}/\d{1,2}/\d{4}.*?\d{1,2}:\d{2})"""), // DD/MM/YYYY format
            Regex("""(\d{4}-\d{2}-\d{2}.*?\d{2}:\d{2})""") // YYYY-MM-DD format
        )

        // Context-aware amount detection
        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (amount.isEmpty()) {
                for (pattern in amountPatterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val foundAmount = match.groupValues[1]
                        val cleanAmount = foundAmount.replace(",", "")
                        val amountValue = cleanAmount.toDoubleOrNull()

                        if (amountValue != null && amountValue >= 0.01 && amountValue <= 100000) {
                            amount = "₹$foundAmount"
                            break
                        }
                    }
                }
            }

            if (amount.isEmpty() && lowerLine.contains("amount")) {
                val searchRange = minOf(i + 3, lines.size)
                for (j in (i + 1) until searchRange) {
                    val nextLine = lines[j]
                    for (pattern in amountPatterns) {
                        val match = pattern.find(nextLine)
                        if (match != null) {
                            val foundAmount = match.groupValues[1]
                            val cleanAmount = foundAmount.replace(",", "")
                            val amountValue = cleanAmount.toDoubleOrNull()

                            if (amountValue != null && amountValue >= 0.01 && amountValue <= 100000) {
                                amount = "₹$foundAmount"
                                break
                            }
                        }
                    }
                    if (amount.isNotEmpty()) break
                }
            }
        }

        // Enhanced recipient detection
        val fullText = lines.joinToString("\n")

        for (pattern in recipientPatterns) {
            if (recipient.isEmpty()) {
                val match = pattern.find(fullText)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    if (isValidRecipientName(name)) {
                        recipient = name
                        break
                    }
                }
            }
        }

        if (recipient.isEmpty()) {
            for (line in lines) {
                val trimmedLine = line.trim()
                if (isStandaloneName(trimmedLine)) {
                    recipient = trimmedLine
                    break
                }
            }
        }

        // Simplified line-based note detection
        note = detectNote(lines)

        // Enhanced transaction ID detection
        val transactionIdCandidates = findTransactionIdCandidates(lines)
        if (transactionIdCandidates.isNotEmpty()) {
            transactionId = transactionIdCandidates.maxByOrNull { it.second }?.first ?: ""
        }

// Enhanced date/time detection
        for (line in lines) {
            if (dateTime.isEmpty()) {
                for (pattern in dateTimePatterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val rawDateTime = match.groupValues[1]
                        dateTime = normalizeDateTimeFormat(rawDateTime)
                        break
                    }
                }
            }
        }

        // Bank info detection
        bankInfo = findBankInfo(lines)

        // Display results
        displayParsedResults(amount, recipient, note, dateTime, transactionId, bankInfo, lines, transactionIdCandidates)
    }


    // Add this method after the parsePaymentInfo method
    private fun normalizeDateTimeFormat(rawDateTime: String): String {
        if (rawDateTime.isEmpty()) return ""

        try {
            // Define various input formats from different apps
            val inputFormats = listOf(
                SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.ENGLISH),      // 13 September 2025, 2:40 am
                SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH),       // 13 Sep 2025, 2:40 am
                SimpleDateFormat("h:mm a 'on' d MMM yyyy", Locale.ENGLISH),   // 7:32 PM on 13 Sep 2025
                SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.ENGLISH),      // 13 Sep 2025, 08:55 PM
                SimpleDateFormat("d/M/yyyy h:mm a", Locale.ENGLISH),          // 13/9/2025 2:40 pm
                SimpleDateFormat("d/M/yyyy HH:mm", Locale.ENGLISH),           // 13/9/2025 14:40
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)          // 2025-09-13 14:40
            )

            // Standard output format
            val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

            // Try each input format
            for (inputFormat in inputFormats) {
                try {
                    val date = inputFormat.parse(rawDateTime)
                    if (date != null) {
                        return outputFormat.format(date)
                    }
                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }

            // If no format matches, return original
            return rawDateTime

        } catch (e: Exception) {
            Log.e("MainActivity", "Error normalizing date: $rawDateTime", e)
            return rawDateTime
        }
    }


    // Simplified line-based note detection with PhonePe fix and UPI payment suffix removal
    private fun detectNote(lines: List<String>): String {
        var note = ""

        // Method 1: Check fixed positions where notes typically appear
        val notePositions = listOf(17, 9) // PhonePe, GPay respectively

        for (position in notePositions) {
            if (position < lines.size) {
                val candidateNote = lines[position].trim()

                // Special case: If line 17 contains "powered by", skip note detection entirely
                if (position == 17 && candidateNote.lowercase().contains("powered by")) {
                    note = "" // Explicitly set as not found for PhonePe
                    break
                }

                if (isValidNoteByPosition(candidateNote)) {
                    note = candidateNote
                    break
                }
            }
        }

        // Method 2: ICICI fallback - look after "Remarks" label
        if (note.isEmpty()) {
            for (i in lines.indices) {
                val line = lines[i].trim().lowercase()
                if (line == "remarks" && i + 1 < lines.size) {
                    val candidateNote = lines[i + 1].trim()
                    if (isValidNoteByPosition(candidateNote)) {
                        note = candidateNote
                        break
                    }
                }
            }
        }

        // Remove "-UPI payment" suffix if present
        if (note.isNotEmpty()) {
            note = removeUpiPaymentSuffix(note)
        }

        return note
    }

    // Helper function to remove UPI payment suffix
    private fun removeUpiPaymentSuffix(note: String): String {
        val upiSuffixes = listOf(
            "-UPI payment",
            "-UPI Payment",
            " - UPI payment",
            " - UPI Payment",
            "-upi payment",
            " - upi payment"
        )

        var cleanedNote = note
        for (suffix in upiSuffixes) {
            if (cleanedNote.endsWith(suffix, ignoreCase = true)) {
                cleanedNote = cleanedNote.substring(0, cleanedNote.length - suffix.length).trim()
                break
            }
        }

        return cleanedNote
    }

    private fun isValidNoteByPosition(text: String): Boolean {
        if (text.length < 1 || text.length > 100) return false

        val lowerText = text.lowercase()

        // Skip your specified patterns
        val skipPatterns = listOf(
            "powered by",
            "unified payments interface",
            "reference #"
        )

        if (skipPatterns.any { lowerText.contains(it) }) {
            return false
        }

        // Skip technical identifiers
        if (text.contains("@") ||
            text.matches(Regex("""[A-Z0-9]{12,}""")) ||
            text.matches(Regex("""XXXXXX\d+""")) ||
            text.matches(Regex("""\d{10,}""")) ||
            text.startsWith("₹") ||
            lowerText.contains("transaction id") ||
            lowerText.contains(" bank")) {
            return false
        }

        // Skip recipient names (proper case names with 2+ words)
        if (text.matches(Regex("""^[A-Z][a-z]+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*$""")) &&
            text.split("\\s+".toRegex()).size >= 2) {
            return false
        }

        return true
    }

    // Helper function to validate recipient names
    private fun isValidRecipientName(name: String): Boolean {
        return name.length > 2 &&
                !name.contains("@") &&
                !name.equals("VPA", ignoreCase = true) &&
                name.matches(Regex("[A-Za-z\\s]+")) && // Only letters and spaces
                name.split("\\s+".toRegex()).size >= 2 && // At least 2 words
                !name.lowercase().contains("bank") &&
                !name.lowercase().contains("payment") &&
                !name.lowercase().contains("transaction") &&
                !name.lowercase().contains("upi")
    }

    // Helper function to detect standalone names
    private fun isStandaloneName(line: String): Boolean {
        return line.matches(Regex("[A-Z][a-z]+\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*")) && // Proper case
                !line.contains("@") &&
                line.length in 5..50 &&
                !line.lowercase().contains("bank") &&
                !line.lowercase().contains("payment") &&
                !line.lowercase().contains("transaction") &&
                !line.lowercase().contains("app") &&
                !line.lowercase().contains("upi")
    }

    // Enhanced transaction ID detection with better context scoring
    private fun findTransactionIdCandidates(lines: List<String>): List<Pair<String, Int>> {
        val candidates = mutableListOf<Pair<String, Int>>()

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            // Build context from surrounding lines
            val contextLines = listOfNotNull(
                if (i > 0) lines[i-1] else null,
                line,
                if (i < lines.size - 1) lines[i+1] else null
            )
            val context = contextLines.joinToString(" ").lowercase()

            // Transaction ID patterns with different characteristics
            val patterns = mapOf(
                Regex("""T\d{15,25}""") to 5, // UPI format starting with T
                Regex("""[A-Z0-9]{15,25}""") to 3, // Long alphanumeric
                Regex("""\d{12,20}""") to 2, // Long numeric
                Regex("""[A-Z]{2,4}\d{10,20}""") to 4, // Letters followed by numbers
                Regex("""XXXXXX\d{4,}""") to 3, // Masked format
                Regex("""[A-Z0-9]{10,14}""") to 1 // Medium length alphanumeric
            )

            for ((pattern, baseScore) in patterns) {
                val matches = pattern.findAll(line)
                for (match in matches) {
                    val candidate = match.value
                    var score = baseScore

                    // Context-based scoring
                    when {
                        context.contains("transaction") && context.contains("id") -> score += 10
                        context.contains("transaction id") -> score += 8
                        context.contains("upi transaction id") -> score += 9
                        context.contains("reference") -> score += 6
                        context.contains("utr") -> score += 7
                        lowerLine.contains("transaction") -> score += 4
                        lowerLine.contains("id") -> score += 2
                    }

                    // Pattern-specific scoring
                    when {
                        candidate.startsWith("T") && candidate.length > 15 -> score += 3
                        candidate.length in 15..22 -> score += 2
                        candidate.matches(Regex("""\d+""")) && candidate.length > 10 -> score += 1
                    }

                    // Avoid common false positives
                    when {
                        candidate.contains("911") -> score -= 2 // Phone numbers
                        candidate.length < 10 -> score -= 3
                        candidate.length > 25 -> score -= 2
                    }

                    if (score > 0) {
                        candidates.add(Pair(candidate, score))
                    }
                }
            }
        }

        return candidates.distinctBy { it.first }.sortedByDescending { it.second }
    }

    // Helper function to find bank information - improved to avoid labels
    private fun findBankInfo(lines: List<String>): String {
        val bankKeywords = listOf("icici bank", "axis bank", "sbi", "hdfc bank", "kotak bank", "paytm payments bank", "state bank of india")
        val avoidLabels = listOf("banking name", "bank name", "from bank", "to bank", "powered by")

        for (line in lines) {
            val lowerLine = line.lowercase().trim()

            // Skip if it's just a label or contains "powered by"
            if (avoidLabels.any { lowerLine == it || lowerLine.contains(it) }) {
                continue
            }

            // Look for actual bank names
            for (bankKeyword in bankKeywords) {
                if (lowerLine.contains(bankKeyword)) {
                    if (!lowerLine.contains("@") && line.length < 100) {
                        return line.trim()
                    }
                }
            }
        }
        return ""
    }

    // Add this method to MainActivity.kt
    // UPDATE the launchEditActivity method to include category
    private fun launchEditActivity(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        category: String = "cat_other"  // ADD THIS PARAMETER
    ) {
        val intent = Intent(this, EditPaymentActivity::class.java).apply {
            putExtra("amount", amount)
            putExtra("recipient", recipient)
            putExtra("note", note)
            putExtra("dateTime", dateTime)
            putExtra("transactionId", transactionId)
            putExtra("bankInfo", bankInfo)
            putExtra("category", category)  // ADD THIS
        }
        startActivityForResult(intent, EDIT_REQUEST_CODE)
    }

    // Update this method in MainActivity.kt
    private fun displayParsedResults(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        lines: List<String>, transactionIdCandidates: List<Pair<String, Int>>
    ) {
        // Show only the clean payment details
        val parsedInfo = buildString {
            appendLine("===== PAYMENT DETAILS =====")
            appendLine()
            appendLine("💰 Amount: ${amount.ifEmpty { "Not found" }}")
            appendLine("👤 Recipient: ${recipient.ifEmpty { "Not found" }}")
            appendLine("📝 Note: ${note.ifEmpty { "Not found" }}")
            appendLine("📅 Date/Time: ${dateTime.ifEmpty { "Not found" }}")
            appendLine("🆔 Transaction ID: ${transactionId.ifEmpty { "Not found" }}")
            appendLine("🏦 Bank: ${bankInfo.ifEmpty { "Not found" }}")
            appendLine()
            appendLine("Ready to save or edit the details above.")
        }

        statusText.text = parsedInfo

        // Show the buttons container
        val buttonsContainer = findViewById<LinearLayout>(R.id.buttonsContainer)
        buttonsContainer.visibility = LinearLayout.VISIBLE

        // Setup button click listeners
        setupButtonListeners(amount, recipient, note, dateTime, transactionId, bankInfo)
    }

    // Add this new method to MainActivity.kt
    // UPDATE setupButtonListeners to include category
    private fun setupButtonListeners(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String
    ) {
        val saveButton = findViewById<Button>(R.id.saveButton)
        val editButton = findViewById<Button>(R.id.editButton)

        // Save button - directly save/export the data as-is with default category
        saveButton.setOnClickListener {
            savePaymentDetails(amount, recipient, note, dateTime, transactionId, bankInfo, "cat_other")
        }

        // Edit button - launch edit activity
        editButton.setOnClickListener {
            launchEditActivity(amount, recipient, note, dateTime, transactionId, bankInfo, "cat_other")
        }
    }

    // Updated savePaymentDetails method with CSV functionality
    // UPDATE savePaymentDetails to include category
    private fun savePaymentDetails(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        category: String = "cat_other"  // ADD THIS PARAMETER
    ) {
        // Create transaction object
        val transaction = PaymentTransaction(
            amount = amount,
            recipient = recipient,
            note = note,
            dateTime = dateTime,
            transactionId = transactionId,
            bankInfo = bankInfo,
            category = category  // ADD THIS
        )

        // Save to CSV
        val success = csvManager.saveTransaction(transaction)

        if (success) {
            val categoryDisplay = categoryManager.getCategoryDisplayName(category)
            val savedInfo = buildString {
                appendLine("✅ PAYMENT DETAILS SAVED!")
                appendLine()
                appendLine("Amount: $amount")
                appendLine("Recipient: $recipient")
                appendLine("Category: $categoryDisplay")  // ADD THIS
                appendLine("Note: ${note.ifEmpty { "No note" }}")
                appendLine("Date/Time: $dateTime")
                appendLine("Transaction ID: $transactionId")
                appendLine("Bank: ${bankInfo.ifEmpty { "No bank info" }}")
                appendLine()
                appendLine("💾 Saved to CSV file!")
                appendLine("📊 Total transactions: ${csvManager.getTransactionCount()}")
                appendLine()
                appendLine("💡 Long press 'Process Image' for more options")
            }

            statusText.text = savedInfo

            // Show a toast confirmation
            android.widget.Toast.makeText(this, "Payment details saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            statusText.text = "❌ Error saving payment details. Please try again."
            android.widget.Toast.makeText(this, "Error saving transaction", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Hide buttons after saving
        val buttonsContainer = findViewById<LinearLayout>(R.id.buttonsContainer)
        buttonsContainer.visibility = LinearLayout.GONE
    }

    // UPDATE showExportOptions to add Settings option
    private fun showExportOptions() {
        val options = arrayOf(
            "📊 View Transaction History (${csvManager.getTransactionCount()})",
            "📋 View All Transactions (Text)",
            "📤 Export CSV to Downloads",
            "⚙️ Settings (Categories & Export)",  // ADD THIS
            "ℹ️ Show CSV File Info",
            "🗑️ Clear All Data (Testing)"
        )

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Data Management")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openTransactionHistory()
                1 -> showAllTransactions()
                2 -> exportCsvToDownloads()
                3 -> openSettings()  // ADD THIS
                4 -> showCsvFileInfo()
                5 -> confirmClearAllData()
            }
        }
        builder.show()
    }
    // ADD this new method
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openTransactionHistory() {
        val intent = Intent(this, TransactionHistoryActivity::class.java)
        startActivity(intent)
    }

    private fun showAllTransactions() {
        val transactions = csvManager.getAllTransactions()

        if (transactions.isEmpty()) {
            statusText.text = "📝 No transactions found.\n\nStart by sharing a payment screenshot!"
            return
        }

        val transactionsList = buildString {
            appendLine("📊 ALL TRANSACTIONS (${transactions.size})")
            appendLine("=".repeat(40))
            appendLine()

            transactions.forEachIndexed { index, transaction ->
                appendLine("${index + 1}. ${transaction.amount}")
                appendLine("   👤 ${transaction.recipient}")
                if (transaction.note.isNotEmpty()) {
                    appendLine("   📝 ${transaction.note}")
                }
                appendLine("   📅 ${transaction.dateTime}")
                if (transaction.bankInfo.isNotEmpty()) {
                    appendLine("   🏦 ${transaction.bankInfo}")
                }
                appendLine("   🆔 ${transaction.transactionId}")
                appendLine("   ⏰ Saved: ${transaction.createdAt}")
                appendLine()
            }

            appendLine("💡 Long press 'Process Image' for more options")
        }

        statusText.text = transactionsList
    }

    private fun exportCsvToDownloads() {
        // No permission check needed for Android 10+
        // For Android 9 and below, permission is handled differently

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Only check permission for Android 9 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE)
                return
            }
        }

        val exportedFile = csvManager.exportToDownloads()

        if (exportedFile != null) {
            statusText.text = buildString {
                appendLine("📤 CSV EXPORTED SUCCESSFULLY!")
                appendLine()
                appendLine("📁 File: ${exportedFile.name}")
                appendLine("📍 Location: Downloads folder")
                appendLine("📊 Transactions: ${csvManager.getTransactionCount()}")
                appendLine()
                appendLine("✅ You can now:")
                appendLine("• Open with Excel/Google Sheets")
                appendLine("• Share via email/WhatsApp")
                appendLine("• Backup to cloud storage")
                appendLine()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appendLine("💡 Check your Downloads folder in Files app")
                }
            }

            android.widget.Toast.makeText(this,
                "CSV exported to Downloads!",
                android.widget.Toast.LENGTH_LONG).show()
        } else {
            statusText.text = "❌ Export failed. Please try again."
            android.widget.Toast.makeText(this, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCsvFileInfo() {
        val info = csvManager.getCsvFileInfo()
        statusText.text = buildString {
            appendLine("📁 CSV FILE INFORMATION")
            appendLine("=" .repeat(30))
            appendLine()
            appendLine(info)
            appendLine()
            appendLine("💡 Long press 'Process Image' for more options")
        }
    }

    private fun confirmClearAllData() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("⚠️ Clear All Data")
        builder.setMessage("This will permanently delete all ${csvManager.getTransactionCount()} saved transactions. This cannot be undone!")
        builder.setPositiveButton("Delete All") { _, _ ->
            val success = csvManager.clearAllTransactions()
            if (success) {
                statusText.text = "🗑️ All transaction data cleared.\n\nReady for new transactions!"
                android.widget.Toast.makeText(this, "All data cleared", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "Error clearing data", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // Handle permission result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportCsvToDownloads()
                } else {
                    android.widget.Toast.makeText(this,
                        "Storage permission needed for export",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // UPDATE onActivityResult to handle category
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Get updated data from EditPaymentActivity
            val updatedAmount = data.getStringExtra("amount") ?: ""
            val updatedRecipient = data.getStringExtra("recipient") ?: ""
            val updatedDateTime = data.getStringExtra("dateTime") ?: ""
            val updatedTransactionId = data.getStringExtra("transactionId") ?: ""
            val updatedNote = data.getStringExtra("note") ?: ""
            val updatedBankInfo = data.getStringExtra("bankInfo") ?: ""
            val updatedCategory = data.getStringExtra("category") ?: "cat_other"  // ADD THIS

            // Save the updated details
            savePaymentDetails(updatedAmount, updatedRecipient, updatedNote,
                updatedDateTime, updatedTransactionId, updatedBankInfo, updatedCategory)  // ADD category
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}