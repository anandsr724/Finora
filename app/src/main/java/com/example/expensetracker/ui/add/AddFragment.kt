package com.example.expensetracker.ui.add

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.expensetracker.*
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AddFragment : Fragment() {
    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var processButton: Button
    private var currentBitmap: Bitmap? = null
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager

    companion object {
        private const val EDIT_REQUEST_CODE = 1001
        private const val PICK_IMAGE_REQUEST = 1003
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add, container, false)
        csvManager = CSVManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        categoryManager.initializeDefaultCategories()

        imageView = view.findViewById(R.id.imageView)
        statusText = view.findViewById(R.id.statusText)
        processButton = view.findViewById(R.id.processButton)

        val selectImageButton = view.findViewById<Button>(R.id.selectImageButton)
        selectImageButton.setOnClickListener {
            openGallery()
        }

        processButton.setOnClickListener {
            performMLKitOCR()
        }

        // Add manual entry button
        val manualEntryButton = view.findViewById<Button>(R.id.manualEntryButton)
        manualEntryButton?.setOnClickListener {
            openManualEntryForm()
        }

        // Check if image URI was passed from HomeFragment
        val imageUriString = arguments?.getString("imageUri")
        if (!imageUriString.isNullOrEmpty()) {
            val imageUri = Uri.parse(imageUriString)
            convertToBitmap(imageUri)
        }

        return view
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun convertToBitmap(uri: Uri) {
        try {
            currentBitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(requireActivity().contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            imageView.setImageBitmap(currentBitmap)
            view?.findViewById<MaterialCardView>(R.id.imagePreviewCard)?.visibility = View.VISIBLE
            view?.findViewById<MaterialCardView>(R.id.processButtonCard)?.visibility = View.VISIBLE
            statusText.text = "Image loaded! Size: ${currentBitmap?.width}x${currentBitmap?.height}"
            processButton.isEnabled = true
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

        val amountPatterns = listOf(
            Regex("""₹\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
            Regex("""Rs\.?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""INR\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*₹"""),
            Regex("""^([0-9]+(?:\.[0-9]{1,2})?)$"""),
            Regex("""Amount.*?₹\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Amount.*?([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        val recipientPatterns = listOf(
            Regex("""(?:To|TO)\s+([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)+)"""),
            Regex("""(?:Paid to|Sent to)\s+([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)+)"""),
            Regex("""Banking Name\s*:\s*([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)+)"""),
            Regex("""^([A-Z][a-z]+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)$""")
        )

        val dateTimePatterns = listOf(
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{1,2}:\d{2}\s*[ap]m)""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2}:\d{2}\s*[AP]M\s+on\s+\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2}/\d{1,2}/\d{4}.*?\d{1,2}:\d{2})"""),
            Regex("""(\d{4}-\d{2}-\d{2}.*?\d{2}:\d{2})""")
        )

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

        note = detectNote(lines)

        val transactionIdCandidates = findTransactionIdCandidates(lines)
        if (transactionIdCandidates.isNotEmpty()) {
            transactionId = transactionIdCandidates.maxByOrNull { it.second }?.first ?: ""
        }

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

        bankInfo = findBankInfo(lines)

        displayParsedResults(amount, recipient, note, dateTime, transactionId, bankInfo, lines, transactionIdCandidates)
    }

    private fun normalizeDateTimeFormat(rawDateTime: String): String {
        if (rawDateTime.isEmpty()) return ""

        try {
            val inputFormats = listOf(
                SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.ENGLISH),
                SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH),
                SimpleDateFormat("h:mm a 'on' d MMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy h:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
            )

            val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

            for (inputFormat in inputFormats) {
                try {
                    val date = inputFormat.parse(rawDateTime)
                    if (date != null) {
                        return outputFormat.format(date)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            return rawDateTime

        } catch (e: Exception) {
            Log.e("AddFragment", "Error normalizing date: $rawDateTime", e)
            return rawDateTime
        }
    }

    private fun detectNote(lines: List<String>): String {
        var note = ""

        val notePositions = listOf(17, 9)

        for (position in notePositions) {
            if (position < lines.size) {
                val candidateNote = lines[position].trim()

                if (position == 17 && candidateNote.lowercase().contains("powered by")) {
                    note = ""
                    break
                }

                if (isValidNoteByPosition(candidateNote)) {
                    note = candidateNote
                    break
                }
            }
        }

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

        if (note.isNotEmpty()) {
            note = removeUpiPaymentSuffix(note)
        }

        return note
    }

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

        val skipPatterns = listOf(
            "powered by",
            "unified payments interface",
            "reference #"
        )

        if (skipPatterns.any { lowerText.contains(it) }) {
            return false
        }

        if (text.contains("@") ||
            text.matches(Regex("""[A-Z0-9]{12,}""")) ||
            text.matches(Regex("""XXXXXX\d+""")) ||
            text.matches(Regex("""\d{10,}""")) ||
            text.startsWith("₹") ||
            lowerText.contains("transaction id") ||
            lowerText.contains(" bank")) {
            return false
        }

        if (text.matches(Regex("""^[A-Z][a-z]+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*$""")) &&
            text.split("\\s+".toRegex()).size >= 2) {
            return false
        }

        return true
    }

    private fun isValidRecipientName(name: String): Boolean {
        return name.length > 2 &&
                !name.contains("@") &&
                !name.equals("VPA", ignoreCase = true) &&
                name.matches(Regex("[A-Za-z\\s]+")) &&
                name.split("\\s+".toRegex()).size >= 2 &&
                !name.lowercase().contains("bank") &&
                !name.lowercase().contains("payment") &&
                !name.lowercase().contains("transaction") &&
                !name.lowercase().contains("upi")
    }

    private fun isStandaloneName(line: String): Boolean {
        return line.matches(Regex("[A-Z][a-z]+\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*")) &&
                !line.contains("@") &&
                line.length in 5..50 &&
                !line.lowercase().contains("bank") &&
                !line.lowercase().contains("payment") &&
                !line.lowercase().contains("transaction") &&
                !line.lowercase().contains("app") &&
                !line.lowercase().contains("upi")
    }

    private fun findTransactionIdCandidates(lines: List<String>): List<Pair<String, Int>> {
        val candidates = mutableListOf<Pair<String, Int>>()

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            val contextLines = listOfNotNull(
                if (i > 0) lines[i-1] else null,
                line,
                if (i < lines.size - 1) lines[i+1] else null
            )
            val context = contextLines.joinToString(" ").lowercase()

            val patterns = mapOf(
                Regex("""T\d{15,25}""") to 5,
                Regex("""[A-Z0-9]{15,25}""") to 3,
                Regex("""\d{12,20}""") to 2,
                Regex("""[A-Z]{2,4}\d{10,20}""") to 4,
                Regex("""XXXXXX\d{4,}""") to 3,
                Regex("""[A-Z0-9]{10,14}""") to 1
            )

            for ((pattern, baseScore) in patterns) {
                val matches = pattern.findAll(line)
                for (match in matches) {
                    val candidate = match.value
                    var score = baseScore

                    when {
                        context.contains("transaction") && context.contains("id") -> score += 10
                        context.contains("transaction id") -> score += 8
                        context.contains("upi transaction id") -> score += 9
                        context.contains("reference") -> score += 6
                        context.contains("utr") -> score += 7
                        lowerLine.contains("transaction") -> score += 4
                        lowerLine.contains("id") -> score += 2
                    }

                    when {
                        candidate.startsWith("T") && candidate.length > 15 -> score += 3
                        candidate.length in 15..22 -> score += 2
                        candidate.matches(Regex("""\d+""")) && candidate.length > 10 -> score += 1
                    }

                    when {
                        candidate.contains("911") -> score -= 2
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

    private fun findBankInfo(lines: List<String>): String {
        val bankKeywords = listOf("icici bank", "axis bank", "sbi", "hdfc bank", "kotak bank", "paytm payments bank", "state bank of india")
        val avoidLabels = listOf("banking name", "bank name", "from bank", "to bank", "powered by")

        for (line in lines) {
            val lowerLine = line.lowercase().trim()

            if (avoidLabels.any { lowerLine == it || lowerLine.contains(it) }) {
                continue
            }

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

    private fun launchEditActivity(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        category: String = "cat_other"
    ) {
        val intent = Intent(requireActivity(), EditPaymentActivity::class.java).apply {
            putExtra("amount", amount)
            putExtra("recipient", recipient)
            putExtra("note", note)
            putExtra("dateTime", dateTime)
            putExtra("transactionId", transactionId)
            putExtra("bankInfo", bankInfo)
            putExtra("category", category)
        }
        startActivityForResult(intent, EDIT_REQUEST_CODE)
    }

    private fun openManualEntryForm() {
        // Launch EditPaymentActivity with empty fields for manual entry
        val intent = Intent(requireActivity(), EditPaymentActivity::class.java).apply {
            putExtra("amount", "")
            putExtra("recipient", "")
            putExtra("note", "")
            putExtra("dateTime", "")
            putExtra("transactionId", "")
            putExtra("bankInfo", "")
            putExtra("category", "cat_other")
            putExtra("isManualEntry", true)  // Flag to indicate this is manual entry
        }
        startActivityForResult(intent, EDIT_REQUEST_CODE)
    }

    private fun displayParsedResults(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        lines: List<String>, transactionIdCandidates: List<Pair<String, Int>>
    ) {
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

        val buttonsContainer = view?.findViewById<LinearLayout>(R.id.buttonsContainer)
        buttonsContainer?.visibility = LinearLayout.VISIBLE

        setupButtonListeners(amount, recipient, note, dateTime, transactionId, bankInfo)
    }

    private fun setupButtonListeners(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String
    ) {
        val saveButton = view?.findViewById<Button>(R.id.saveButton)
        val editButton = view?.findViewById<Button>(R.id.editButton)

        saveButton?.setOnClickListener {
            savePaymentDetails(amount, recipient, note, dateTime, transactionId, bankInfo, "cat_other")
        }

        editButton?.setOnClickListener {
            launchEditActivity(amount, recipient, note, dateTime, transactionId, bankInfo, "cat_other")
        }
    }

    private fun savePaymentDetails(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        category: String = "cat_other"
    ) {
        val transaction = PaymentTransaction(
            amount = amount,
            recipient = recipient,
            note = note,
            dateTime = dateTime,
            transactionId = transactionId,
            bankInfo = bankInfo,
            category = category
        )

        val success = csvManager.saveTransaction(transaction)

        if (success) {
            val categoryDisplay = categoryManager.getCategoryDisplayName(category)
            val savedInfo = buildString {
                appendLine("✅ PAYMENT DETAILS SAVED!")
                appendLine()
                appendLine("Amount: $amount")
                appendLine("Recipient: $recipient")
                appendLine("Category: $categoryDisplay")
                appendLine("Note: ${note.ifEmpty { "No note" }}")
                appendLine("Date/Time: $dateTime")
                appendLine("Transaction ID: $transactionId")
                appendLine("Bank: ${bankInfo.ifEmpty { "No bank info" }}")
                appendLine()
                appendLine("💾 Saved to CSV file!")
                appendLine("📊 Total transactions: ${csvManager.getTransactionCount()}")
            }

            statusText.text = savedInfo

            android.widget.Toast.makeText(requireContext(), "Payment details saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            statusText.text = "❌ Error saving payment details. Please try again."
            android.widget.Toast.makeText(requireContext(), "Error saving transaction", android.widget.Toast.LENGTH_SHORT).show()
        }

        val buttonsContainer = view?.findViewById<LinearLayout>(R.id.buttonsContainer)
        buttonsContainer?.visibility = LinearLayout.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            val imageUri = data.data
            if (imageUri != null) {
                imageView.setImageURI(imageUri)
                view?.findViewById<MaterialCardView>(R.id.imagePreviewCard)?.visibility = View.VISIBLE
                view?.findViewById<MaterialCardView>(R.id.processButtonCard)?.visibility = View.VISIBLE
                statusText.text = "Image received! Ready to process."
                processButton.isEnabled = true
                convertToBitmap(imageUri)
            }
        }

        if (requestCode == EDIT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val updatedAmount = data.getStringExtra("amount") ?: ""
            val updatedRecipient = data.getStringExtra("recipient") ?: ""
            val updatedDateTime = data.getStringExtra("dateTime") ?: ""
            val updatedTransactionId = data.getStringExtra("transactionId") ?: ""
            val updatedNote = data.getStringExtra("note") ?: ""
            val updatedBankInfo = data.getStringExtra("bankInfo") ?: ""
            val updatedCategory = data.getStringExtra("category") ?: "cat_other"

            savePaymentDetails(updatedAmount, updatedRecipient, updatedNote,
                updatedDateTime, updatedTransactionId, updatedBankInfo, updatedCategory)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}
