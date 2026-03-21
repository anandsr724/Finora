package com.example.expensetracker.ui.add

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.expensetracker.*
import com.example.expensetracker.CurrencyManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AddFragment : Fragment() {
    private lateinit var imageView: ImageView
    private lateinit var imageInfoText: TextView
    private lateinit var statusText: TextView
    private lateinit var processButton: Button
    private lateinit var imagePreviewCard: MaterialCardView
    private lateinit var processButtonCard: MaterialCardView
    private lateinit var statusCard: MaterialCardView
    private var currentBitmap: Bitmap? = null
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    // Pending OCR result — preserved so sheet can be re-shown if Edit is cancelled
    private var pendingAmount = ""
    private var pendingRecipient = ""
    private var pendingNote = ""
    private var pendingDateTime = ""
    private var pendingTransactionId = ""
    private var pendingBankInfo = ""
    private var pendingCurrency = ""
    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var ocrProgressBar: android.widget.ProgressBar
    private lateinit var retryManualButton: MaterialButton

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) convertToBitmap(imageUri)
        }
    }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
            if (pendingAmount.isNotEmpty() || pendingRecipient.isNotEmpty()) {
                val restoredCurrency = pendingCurrency.ifEmpty { CurrencyManager.getDefault(requireContext()) }
                displayParsedResults(
                    pendingAmount, pendingRecipient, pendingNote,
                    pendingDateTime, pendingTransactionId, pendingBankInfo,
                    emptyList(), emptyList(), currency = restoredCurrency, instant = true
                )
            }
        } else if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val updatedAmount = data.getStringExtra("amount") ?: ""
            val updatedRecipient = data.getStringExtra("recipient") ?: ""
            val updatedDateTime = data.getStringExtra("dateTime") ?: ""
            val updatedTransactionId = data.getStringExtra("transactionId") ?: ""
            val updatedNote = data.getStringExtra("note") ?: ""
            val updatedBankInfo = data.getStringExtra("bankInfo") ?: ""
            val updatedCategory = data.getStringExtra("category") ?: "cat_other"
            val updatedCurrency = data.getStringExtra("currency") ?: CurrencyManager.getDefault(requireContext())
            val updatedType = data.getStringExtra("type") ?: "expense"
            savePaymentDetails(updatedAmount, updatedRecipient, updatedNote,
                updatedDateTime, updatedTransactionId, updatedBankInfo, updatedCategory, updatedCurrency, updatedType)
        }
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
        imageInfoText = view.findViewById(R.id.imageInfoText)
        statusText = view.findViewById(R.id.statusText)
        processButton = view.findViewById(R.id.processButton)
        imagePreviewCard = view.findViewById(R.id.imagePreviewCard)
        processButtonCard = view.findViewById(R.id.processButtonCard)
        statusCard = view.findViewById(R.id.statusCard)
        ocrProgressBar = view.findViewById(R.id.ocrProgressBar)
        retryManualButton = view.findViewById(R.id.retryManualButton)
        retryManualButton.setOnClickListener { openManualEntryForm() }

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

        // Check if image URI was passed from HomeFragment — load preview, wait for user to process
        val imageUriString = arguments?.getString("imageUri")
        if (!imageUriString.isNullOrEmpty()) {
            val imageUri = Uri.parse(imageUriString)
            convertToBitmap(imageUri)
        }

        return view
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
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
            imagePreviewCard.visibility = View.VISIBLE
            imageInfoText.text = "${currentBitmap?.width} × ${currentBitmap?.height} px"
            processButtonCard.visibility = View.VISIBLE
            processButton.isEnabled = true
        } catch (e: Exception) {
            statusCard.visibility = View.VISIBLE
            statusText.text = "Error loading image: ${e.message}"
        }
    }

    private fun performMLKitOCR() {
        val bitmap = currentBitmap

        if (bitmap == null) {
            statusText.text = "No image to process"
            return
        }

        statusCard.visibility = View.VISIBLE
        statusText.text = "Processing image…"
        ocrProgressBar.visibility = View.VISIBLE
        retryManualButton.visibility = View.GONE
        processButton.isEnabled = false

        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    processButton.isEnabled = true
                    ocrProgressBar.visibility = View.GONE

                    if (visionText.text.isNotBlank()) {
                        statusText.text = "Extracting details…"
                        betaSaveScreenshot(bitmap)
                        val detailedText = buildDetailedText(visionText)
                        parsePaymentInfo(visionText.text, detailedText)
                    } else {
                        statusText.text = "No text found in this image. Try a clearer screenshot or enter details manually."
                        retryManualButton.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    processButton.isEnabled = true
                    ocrProgressBar.visibility = View.GONE
                    statusText.text = "Could not read image: ${e.message}"
                    retryManualButton.visibility = View.VISIBLE
                }

        } catch (e: IOException) {
            processButton.isEnabled = true
            ocrProgressBar.visibility = View.GONE
            statusText.text = "Error processing image: ${e.message}"
            retryManualButton.visibility = View.VISIBLE
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
        var detectedCurrency = CurrencyManager.getDefault(requireContext())
        var recipient = ""
        var note = ""
        var transactionId = ""
        var dateTime = ""
        var bankInfo = ""

        // Pair of (regex, detected currency code) — null currency means use app default
        val currencyAmountPatterns = listOf(
            Pair(Regex("""₹\s*([0-9,]+(?:\.[0-9]{1,2})?)"""), "INR"),
            Pair(Regex("""Rs\.?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "INR"),
            Pair(Regex("""INR\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "INR"),
            Pair(Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*₹"""), "INR"),
            Pair(Regex("""\$\s*([0-9,]+(?:\.[0-9]{1,2})?)"""), "USD"),
            Pair(Regex("""USD\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "USD"),
            Pair(Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*USD""", RegexOption.IGNORE_CASE), "USD"),
            Pair(Regex("""€\s*([0-9,]+(?:\.[0-9]{1,2})?)"""), "EUR"),
            Pair(Regex("""EUR\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "EUR"),
            Pair(Regex("""£\s*([0-9,]+(?:\.[0-9]{1,2})?)"""), "GBP"),
            Pair(Regex("""GBP\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "GBP"),
            Pair(Regex("""¥\s*([0-9,]+(?:\.[0-9]{1,2})?)"""), "JPY"),
            Pair(Regex("""JPY\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "JPY"),
            // Common OCR misread of ₹ (%, z, or other symbols in decorative fonts)
            Pair(Regex("""^[%z₹]\s*([0-9,]{1,8}(?:\.[0-9]{1,2})?)$""", RegexOption.IGNORE_CASE), "INR"),
            Pair(Regex("""Amount.*?([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), null),
            // Bare number fallback — capped at 8 digits to avoid matching 12-digit transaction IDs
            Pair(Regex("""^([0-9]{1,8}(?:\.[0-9]{1,2})?)$"""), null)
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
                // Context of previous line — used to skip bare numbers that follow a transaction ID label
                val prevLineLower = if (i > 0) lines[i - 1].lowercase() else ""
                val isAfterIdContext = prevLineLower.contains("transaction") ||
                        prevLineLower.contains("reference") ||
                        prevLineLower.contains("utr") ||
                        prevLineLower.contains("google transaction") ||
                        prevLineLower.contains("upi transaction")

                for ((pattern, currencyCode) in currencyAmountPatterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val foundAmount = match.groupValues[1]
                        val cleanAmount = foundAmount.replace(",", "")
                        val amountValue = cleanAmount.toDoubleOrNull()

                        // Skip bare numbers (no currency symbol/keyword) that follow an ID-context line
                        val isBareNumber = currencyCode == null && !line.contains("Amount", ignoreCase = true)
                        if (isBareNumber && isAfterIdContext) continue

                        if (amountValue != null && amountValue >= 0.01) {
                            amount = foundAmount
                            if (currencyCode != null) detectedCurrency = currencyCode
                            break
                        }
                    }
                }
            }

            if (amount.isEmpty() && lowerLine.contains("amount")) {
                val searchRange = minOf(i + 3, lines.size)
                for (j in (i + 1) until searchRange) {
                    val nextLine = lines[j]
                    for ((pattern, currencyCode) in currencyAmountPatterns) {
                        val match = pattern.find(nextLine)
                        if (match != null) {
                            val foundAmount = match.groupValues[1]
                            val cleanAmount = foundAmount.replace(",", "")
                            val amountValue = cleanAmount.toDoubleOrNull()

                            if (amountValue != null && amountValue >= 0.01) {
                                amount = foundAmount
                                if (currencyCode != null) detectedCurrency = currencyCode
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

        displayParsedResults(amount, recipient, note, dateTime, transactionId, bankInfo, lines, transactionIdCandidates, currency = detectedCurrency)
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
        category: String = "cat_other",
        currency: String = CurrencyManager.getDefault(requireContext())
    ) {
        val intent = Intent(requireActivity(), EditPaymentActivity::class.java).apply {
            putExtra("amount", amount)
            putExtra("recipient", recipient)
            putExtra("note", note)
            putExtra("dateTime", dateTime)
            putExtra("transactionId", transactionId)
            putExtra("bankInfo", bankInfo)
            putExtra("category", category)
            putExtra("currency", currency)
        }
        editLauncher.launch(intent)
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
        editLauncher.launch(intent)
    }

    private fun displayParsedResults(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        lines: List<String>, transactionIdCandidates: List<Pair<String, Int>>,
        currency: String = CurrencyManager.getDefault(requireContext()),
        instant: Boolean = false
    ) {
        statusCard.visibility = View.GONE

        val sheet = if (instant)
            BottomSheetDialog(requireContext(), R.style.BottomSheetDialog_NoAnim)
        else
            BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_transaction_detail_sheet, null)
        sheet.setContentView(sheetView)

        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))

        // Category icon (default until user sets category via edit)
        sheetView.findViewById<ImageView>(R.id.detailCategoryIcon)
            .setImageResource(CategoryIconHelper.getIconResId("cat_other"))

        // Amount
        val numericAmount = CurrencyManager.parseAmount(amount)
        val currencySymbol = CurrencyManager.getSymbol(currency)
        sheetView.findViewById<TextView>(R.id.detailAmount).text =
            if (numericAmount > 0) "$currencySymbol${fmt.format(numericAmount)}" else amount.ifEmpty { "—" }

        // Category badge
        sheetView.findViewById<TextView>(R.id.detailCategoryBadge).text =
            categoryManager.getCategoryDisplayName("cat_other")

        // Recipient
        sheetView.findViewById<TextView>(R.id.detailRecipient).text = recipient.ifEmpty { "—" }

        // Note
        val noteRow = sheetView.findViewById<LinearLayout>(R.id.detailNoteRow)
        val noteDivider = sheetView.findViewById<View>(R.id.detailNoteDivider)
        if (note.isNotEmpty()) {
            sheetView.findViewById<TextView>(R.id.detailNote).text = note
            noteRow.visibility = View.VISIBLE
            noteDivider.visibility = View.VISIBLE
        } else {
            noteRow.visibility = View.GONE
            noteDivider.visibility = View.GONE
        }

        // Date/Time
        sheetView.findViewById<TextView>(R.id.detailDateTime).text = dateTime.ifEmpty { "—" }

        // Payment method
        sheetView.findViewById<TextView>(R.id.detailPaymentMethod).text = bankInfo.ifEmpty { "—" }

        // Transaction ID
        sheetView.findViewById<TextView>(R.id.detailTransactionId).text = transactionId.ifEmpty { "—" }

        // Close button
        sheetView.findViewById<ImageButton>(R.id.closeDetailSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        // Primary action: Save immediately
        sheetView.findViewById<MaterialButton>(R.id.detailEditButton).apply {
            text = "Save"
            setOnClickListener {
                sheet.dismiss()
                savePaymentDetails(amount, recipient, note, dateTime, transactionId, bankInfo,
                    "cat_other", currency)
            }
        }

        // Secondary action: Review & Edit before saving
        sheetView.findViewById<MaterialButton>(R.id.detailDeleteButton).apply {
            text = "Review & Edit"
            setTextColor(requireContext().getColor(R.color.primary_indigo))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#EDE9FE")
            )
            setOnClickListener {
                pendingAmount = amount
                pendingRecipient = recipient
                pendingNote = note
                pendingDateTime = dateTime
                pendingTransactionId = transactionId
                pendingBankInfo = bankInfo
                pendingCurrency = currency
                sheet.dismiss()
                launchEditActivity(amount, recipient, note, dateTime, transactionId, bankInfo, "cat_other", currency)
            }
        }

        sheet.show()
    }

    private fun savePaymentDetails(
        amount: String, recipient: String, note: String,
        dateTime: String, transactionId: String, bankInfo: String,
        category: String = "cat_other",
        currency: String = CurrencyManager.getDefault(requireContext()),
        type: String = "expense"
    ) {
        val transaction = PaymentTransaction(
            amount = amount,
            recipient = recipient,
            note = note,
            dateTime = dateTime,
            transactionId = transactionId,
            bankInfo = bankInfo,
            category = category,
            currency = currency,
            type = type
        )

        val success = csvManager.saveTransaction(transaction)

        if (success) {
            Toast.makeText(requireContext(), "Transaction saved!", Toast.LENGTH_SHORT).show()
            pendingAmount = ""; pendingRecipient = ""; pendingNote = ""
            pendingDateTime = ""; pendingTransactionId = ""; pendingBankInfo = ""; pendingCurrency = ""
            // Reset page for next upload
            imagePreviewCard.visibility = View.GONE
            processButtonCard.visibility = View.GONE
            statusCard.visibility = View.GONE
            currentBitmap = null
            processButton.isEnabled = false
        } else {
            Toast.makeText(requireContext(), "Error saving transaction", Toast.LENGTH_SHORT).show()
        }
    }

    /** Beta: save the processed screenshot to Downloads/Finora/Screenshots if the pref is on. */
    private fun betaSaveScreenshot(bitmap: Bitmap) {
        val prefs = requireContext().getSharedPreferences("finora_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("beta_save_screenshots", false)) return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "finora_$timestamp.jpg"

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // API 29+: use MediaStore.Downloads — no permission needed
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/Finora/Screenshots")
                }
                val uri = requireContext().contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: run {
                        Log.e("AddFragment", "Beta: MediaStore insert returned null")
                        return
                    }
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } else {
                // API < 29: WRITE_EXTERNAL_STORAGE is granted via manifest (maxSdkVersion=28)
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w("AddFragment", "Beta: WRITE_EXTERNAL_STORAGE not granted, skipping save")
                    return
                }
                val dir = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Finora/Screenshots"
                )
                dir.mkdirs()
                java.io.FileOutputStream(java.io.File(dir, filename)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            Log.d("AddFragment", "Beta: screenshot saved → $filename")
        } catch (e: Exception) {
            Log.e("AddFragment", "Beta: failed to save screenshot", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}
