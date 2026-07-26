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
import com.example.expensetracker.data.OcrTrackingManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.content.Context
import android.content.DialogInterface
import androidx.core.content.ContextCompat
import com.example.expensetracker.ui.common.applyCategoryDotGlow
import com.example.expensetracker.ui.common.applyGlassBlur
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException
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

    // OCR tracking session — captures predicted vs actual data for dev/training use
    private var isOcrSession = false
    private var ocrSessionId = ""
    private var ocrCapturedAt = ""
    private var ocrWasEdited = false
    private var ocrPredictedAmount = ""
    private var ocrPredictedRecipient = ""
    private var ocrPredictedNote = ""
    private var ocrPredictedDateTime = ""
    private var ocrPredictedTransactionId = ""
    private var ocrPredictedBankInfo = ""
    private var ocrPredictedCurrency = ""
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

        val uploadReceiptRow = view.findViewById<View>(R.id.uploadReceiptRow)
        uploadReceiptRow.setOnClickListener {
            openGallery()
        }

        processButton.setOnClickListener {
            performMLKitOCR()
        }

        val manualEntryRow = view.findViewById<View>(R.id.manualEntryRow)
        manualEntryRow.setOnClickListener {
            openManualEntryForm()
        }

        val importStatementRow = view.findViewById<View>(R.id.importStatementRow)
        importStatementRow.setOnClickListener {
            startActivity(Intent(requireContext(), StatementImportActivity::class.java))
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
            val processedBitmap = preprocessForOCR(bitmap)
            val image = InputImage.fromBitmap(processedBitmap, 0)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    processButton.isEnabled = true
                    ocrProgressBar.visibility = View.GONE

                    processedBitmap.recycle()
                    if (visionText.text.isNotBlank()) {
                        statusText.text = "Extracting details…"
                        val detailedText = buildDetailedText(visionText)
                        Log.d("FINORA_OCR", "=== Raw OCR text ===\n${visionText.text}")
                        val correctedText = fixRupeeSymbolMisreads(visionText.text)
                        val spatialAmount = extractAmountSpatially(visionText)
                        parsePaymentInfo(correctedText, detailedText, spatialAmount)
                    } else {
                        statusText.text = "No text found in this image. Try a clearer screenshot or enter details manually."
                        retryManualButton.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    processedBitmap.recycle()
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

    private fun parsePaymentInfo(rawText: String, detailedText: String, spatialAmount: String? = null) {
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
            // ── Tier A: explicit currency symbol ─────────────────────────────────
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
            // ── Tier B: labeled amounts — e-commerce, food delivery, physical bills ─
            // Handles: "Grand Total: 1,200", "You paid 800", "Net Payable: 450",
            //          "To Pay 250", "Total ₹500", "Order Total: 999", "Charged: 150"
            Pair(Regex("""(?:grand\s+total|order\s+total|bill\s+amount|net\s+payable|to\s+pay|you\s+paid|charged|amount\s+paid|amount\s+due|total\s+amount|total\s+due|total\s+payable|net\s+amount|final\s+amount|subtotal|total\s+bill)[:\s]+(?:₹\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), null),
            Pair(Regex("""Total[:\s]+₹\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), "INR"),
            Pair(Regex("""(?:amount)[:\s]+(?:₹\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE), null),
            // ── Tier C: bare number fallback ─────────────────────────────────────
            // Capped at 8 digits to avoid matching 12-digit transaction IDs
            Pair(Regex("""^([0-9]{1,8}(?:\.[0-9]{1,2})?)$"""), null),
            // Bare comma-formatted amount (e.g., "1,035.58" — ₹ symbol fully dropped by OCR)
            Pair(Regex("""^([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?)$"""), null)
        )

        // (pattern, isMerchant) — isMerchant=true uses loose validation for brand names
        val recipientPatterns = listOf(
            // UPI / bank transfer (high confidence — explicit label)
            Pair(Regex("""(?:Paid to|Sent to|Transferred to)\s+(.+)""", RegexOption.IGNORE_CASE), false),
            Pair(Regex("""(?:^|\n)To\s+([A-Z][A-Za-z\s'&.\-]{2,50})"""), false),
            Pair(Regex("""Banking Name\s*:\s*(.+)""", RegexOption.IGNORE_CASE), false),
            // E-commerce / food delivery / physical bill merchant labels
            Pair(Regex("""(?:Merchant|Store|Vendor|Seller|Biller|Payee)\s*:\s*(.+)""", RegexOption.IGNORE_CASE), true),
            Pair(Regex("""Restaurant\s*:\s*(.+)""", RegexOption.IGNORE_CASE), true),
            Pair(Regex("""Order(?:ed)?\s+from\s+(.+)""", RegexOption.IGNORE_CASE), true),
            // Standalone proper-noun fallback (broadened to allow brand chars)
            Pair(Regex("""^([A-Z][a-z]+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)$"""), false)
        )

        val dateTimePatterns = listOf(
            // "March 11, 2026 at 10:04 PM" — month-first US format (Swiggy payment date)
            Regex("""([A-Za-z]{3,9}\s+\d{1,2},\s+\d{4}\s+at\s+\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // "March 11, 2026, 10:04 PM" — month-first with comma separator
            Regex("""([A-Za-z]{3,9}\s+\d{1,2},\s+\d{4},\s+\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // "Wed, 15 Jan 2024, 10:30 AM" — day-of-week prefix variant
            Regex("""(?:[A-Za-z]{3},\s+)?(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // "15 Jan 2024 at 10:30 AM" — "at" separator
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}\s+at\s+\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // "11th Mar 26, 04:51 pm" — ordinal day + 2-digit year (BHIM UPI)
            Regex("""(\d{1,2}(?:st|nd|rd|th)\s+[A-Za-z]{3,9}\s+\d{2},\s+\d{1,2}:\d{2}\s*[ap]m)""", RegexOption.IGNORE_CASE),
            // "Thu, 02 Oct'25, 12:51 PM" — day-of-week + apostrophe-year (Blinkit)
            Regex("""(?:(?:placed on|ordered on|delivered on)\s+)?(?:[A-Za-z]{3},\s+)?(\d{1,2}\s+[A-Za-z]{3}'\d{2},\s+\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // Original datetime patterns
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{1,2}:\d{2}\s*[ap]m)""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2}:\d{2}\s*[AP]M\s+on\s+\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4},\s+\d{2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE),
            // "15-Jan-2024 10:30" — dash-separated abbreviated month
            Regex("""(\d{1,2}-[A-Za-z]{3}-\d{4}\s+\d{1,2}:\d{2})""", RegexOption.IGNORE_CASE),
            // Slash date + time
            Regex("""(\d{1,2}/\d{1,2}/\d{4}.*?\d{1,2}:\d{2})"""),
            // ISO date + time
            Regex("""(\d{4}-\d{2}-\d{2}.*?\d{2}:\d{2})"""),
            // Date-only fallbacks — only fire when no datetime pattern matched above
            Regex("""(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})"""),
            Regex("""(\d{1,2}/\d{1,2}/\d{4})"""),
            Regex("""(\d{4}-\d{2}-\d{2})""")
        )

        // Collect ALL amount candidates then pick the best by priority.
        // This prevents "Item total ₹857" being chosen over "Paid ₹735.95" on food receipts.
        // Triple = (value, currency, priority)
        val amountCandidates = mutableListOf<Triple<String, String?, Int>>()

        // Spatial bounding-box result (priority 15) — overrides line-by-line candidates when
        // ML Kit's column layout causes labels and amounts to appear in separate text blocks.
        if (spatialAmount != null) {
            val v = spatialAmount.replace(",", "").toDoubleOrNull()
            if (v != null && v >= 0.01 && v <= 99999.99) {
                amountCandidates.add(Triple(spatialAmount, "INR", 15))
                Log.d("FINORA_OCR", "=== Spatial amount: $spatialAmount (priority 15) ===")
            }
        }

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()
            val prevLineLower = if (i > 0) lines[i - 1].lowercase() else ""
            val isAfterIdContext = prevLineLower.contains("transaction") ||
                    prevLineLower.contains("reference") ||
                    prevLineLower.contains("utr") ||
                    prevLineLower.contains("google transaction") ||
                    prevLineLower.contains("upi transaction")

            val priority = getAmountLinePriority(lowerLine)

            // For high-priority total lines with multiple ₹ values (e.g., "Total Bill ₹720 ₹514"),
            // take the LAST match — the second value is the actual price after discount.
            val preferLast = priority >= 6

            val hasLabelKeyword = lowerLine.contains("amount") || lowerLine.contains("total") ||
                    lowerLine.contains("payable") || lowerLine.contains("charged") ||
                    lowerLine.contains("paid") || lowerLine.contains("subtotal")
            val rupeeCount = line.count { it == '₹' }

            var foundDirect = false
            for ((pattern, currencyCode) in currencyAmountPatterns) {
                // For lines with 2+ ₹ symbols (e.g., "₹674 ₹514" after Pass 4/5 fix),
                // extract ALL matches so last-wins can pick the correct final amount.
                val matches: List<MatchResult> = when {
                    rupeeCount >= 2 -> pattern.findAll(line).toList()
                    preferLast -> listOfNotNull(pattern.findAll(line).lastOrNull())
                    else -> listOfNotNull(pattern.find(line))
                }
                var addedAny = false
                for (match in matches) {
                    val foundAmount = match.groupValues[1]
                    val cleanAmount = foundAmount.replace(",", "")
                    val amountValue = cleanAmount.toDoubleOrNull()

                    val isBareNumber = currencyCode == null && !hasLabelKeyword
                    if (isBareNumber && isAfterIdContext) continue

                    if (amountValue != null && amountValue >= 0.01 && amountValue <= 99999.99) {
                        amountCandidates.add(Triple(foundAmount, currencyCode, priority))
                        addedAny = true
                    }
                }
                if (addedAny) {
                    foundDirect = true
                    break
                }
            }

            // Look-ahead: label-only line followed by the amount on the next line(s).
            // Only fires when no direct match was found on the current line.
            // Guard: if multiple amounts are found in the look-ahead window, the OCR has
            // split a two-column layout (labels block / amounts block). In that case the
            // first amount after a "Paid" or "Grand total" label is actually the item-total
            // amount, not the paid amount — so we skip the look-ahead entirely.
            if (!foundDirect) {
                val lookAheadLabels = listOf(
                    "amount", "total", "grand total", "order total", "bill amount",
                    "net payable", "to pay", "charged", "payable",
                    "subtotal", "total due", "total payable", "final amount",
                    "paid"
                )
                if (lookAheadLabels.any { lowerLine.contains(it) }) {
                    val searchRange = minOf(i + 3, lines.size)
                    val found = mutableListOf<Triple<String, String?, Int>>()
                    for (j in (i + 1) until searchRange) {
                        val nextLine = lines[j]
                        for ((pattern, currencyCode) in currencyAmountPatterns) {
                            val match = pattern.find(nextLine)
                            if (match != null) {
                                val foundAmount = match.groupValues[1]
                                val amountValue = foundAmount.replace(",", "").toDoubleOrNull()
                                if (amountValue != null && amountValue >= 0.01 && amountValue <= 99999.99) {
                                    found.add(Triple(foundAmount, currencyCode, priority))
                                    break
                                }
                            }
                        }
                    }
                    // Only trust look-ahead when it found exactly one amount.
                    // Multiple amounts = column layout; skip to avoid picking the wrong one.
                    if (found.size == 1) {
                        amountCandidates.addAll(found)
                    }
                }
            }
        }

        // Debug: log corrected text and all candidates
        Log.d("FINORA_OCR", "=== Corrected OCR text ===\n$rawText")
        Log.d("FINORA_OCR", "=== Amount candidates (${amountCandidates.size}) ===")
        for (c in amountCandidates) {
            Log.d("FINORA_OCR", "  value=${c.first}  currency=${c.second}  priority=${c.third}")
        }

        // Pick best: highest priority wins.
        // Ties broken by LAST occurrence — on column-layout receipts (Swiggy, Blinkit, Zepto)
        // all amounts fall at the same priority since labels and amounts are in separate OCR
        // blocks. The last amount in the stream is always the final paid/bill-total amount.
        val bestAmount = amountCandidates.maxWithOrNull(compareBy({ it.third }, { amountCandidates.indexOf(it) }))
        Log.d("FINORA_OCR", "=== Best amount: ${bestAmount?.first}  priority=${bestAmount?.third} ===")
        if (bestAmount != null) {
            amount = bestAmount.first
            if (bestAmount.second != null) detectedCurrency = bestAmount.second!!
        }

        val fullText = lines.joinToString("\n")

        for ((pattern, isMerchant) in recipientPatterns) {
            if (recipient.isEmpty()) {
                val match = pattern.find(fullText)
                if (match != null) {
                    val name = cleanCapturedRecipient(match.groupValues[1])
                    val isValid = if (isMerchant) isValidMerchantName(name) else isValidRecipientName(name)
                    if (isValid) {
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

    /**
     * Use ML Kit bounding-box coordinates to pair a label keyword with its spatially
     * aligned amount in the right-hand column — handles two-column receipt layouts
     * (Zepto, Blinkit) where labels and amounts are in separate TextBlocks.
     *
     * Returns the last digit sequence found on the line that is spatially to the right
     * of the highest-priority label found (e.g. "Total Bill", "Paid").
     */
    private fun extractAmountSpatially(visionText: com.google.mlkit.vision.text.Text): String? {
        val targetKeywords = listOf("paid", "total bill", "bill total", "grand total", "net payable", "total payable", "amount paid")

        for (keyword in targetKeywords) {
            var labelLine: com.google.mlkit.vision.text.Text.Line? = null
            var labelBlockRight = 0
            var labelCenterY = 0

            outer@ for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    if (line.text.lowercase().contains(keyword)) {
                        val lb = line.boundingBox ?: continue
                        val bb = block.boundingBox ?: continue
                        labelLine = line
                        labelBlockRight = bb.right
                        labelCenterY = lb.centerY()
                        break@outer
                    }
                }
            }
            if (labelLine == null) continue

            val lineHeight = labelLine.boundingBox?.height() ?: 40
            val tolerance = (lineHeight * 1.5).toInt()

            // Find lines in blocks that are to the RIGHT of the label block and vertically aligned
            for (block in visionText.textBlocks) {
                val blockLeft = block.boundingBox?.left ?: continue
                if (blockLeft <= labelBlockRight) continue  // must be a right-hand column block

                for (line in block.lines) {
                    val lineCenterY = line.boundingBox?.centerY() ?: continue
                    if (kotlin.math.abs(lineCenterY - labelCenterY) > tolerance) continue

                    // Apply ₹ misread corrections first so "7227" → "₹227" before extracting
                    val fixedLineText = fixRupeeSymbolMisreads(line.text)
                    // Prefer ₹-prefixed amounts; fall back to raw digit sequences
                    val rupeeMatches = Regex("""₹\s*([0-9,]+(?:\.[0-9]{1,2})?)""").findAll(fixedLineText)
                        .map { it.groupValues[1].replace(",", "").toDoubleOrNull() }
                        .filterNotNull()
                        .filter { it >= 0.01 && it <= 99999.99 }
                        .toList()
                    val numbers = if (rupeeMatches.isNotEmpty()) rupeeMatches else
                        Regex("""[0-9]+(?:\.[0-9]{1,2})?""").findAll(fixedLineText)
                            .map { it.value.toDoubleOrNull() }
                            .filterNotNull()
                            .filter { it >= 0.01 && it <= 99999.99 }
                            .toList()

                    if (numbers.isNotEmpty()) {
                        val found = numbers.last().let {
                            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                        }
                        Log.d("FINORA_OCR", "=== Spatial: keyword='$keyword' → line='${line.text}' → amount=$found ===")
                        return found
                    }
                }
            }
        }
        return null
    }

    /**
     * Correct common ML Kit OCR misread where the ₹ symbol is read as "7".
     * e.g. "₹838" → OCR produces "7838", "₹401.67" → "7401.67"
     *
     * Two correction passes:
     *  1. Standalone lines that are purely "7NNN" → replace leading 7 with ₹
     *  2. After known amount-label keywords, "7NNN" → "₹NNN"
     *
     * Constraint: only applies when "7" is immediately followed by 2–6 digits
     * (no space), so "7 items" or "7th floor" are NOT affected.
     */
    /**
     * Preprocess bitmap before ML Kit OCR to improve ₹ symbol recognition.
     *
     * Converts to grayscale and boosts contrast so thin/colored ₹ glyphs
     * (misread as Z, R, F, T, H in Zepto/Blinkit/Swiggy fonts) become
     * crisp black strokes on a clean background.
     *
     * Formula: newVal = (val - 128) * contrast + 128
     *   - Light backgrounds (Zepto/Blinkit) → pure white, dark text → pure black
     *   - Dark backgrounds (Google Pay, Swiggy dark) → detected and INVERTED first,
     *     so white ₹ glyph becomes black on white — OCR models perform better this way
     */
    private fun preprocessForOCR(source: android.graphics.Bitmap): android.graphics.Bitmap {
        val result = android.graphics.Bitmap.createBitmap(
            source.width, source.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val contrast = 1.8f
        val isDark = isImageDark(source)
        // For dark images: grayscale + invert + contrast → white ₹ on black becomes black on white
        // For light images: grayscale + contrast (existing behaviour)
        // Both formulae: output = -contrast*L + (127*contrast + 128)  [dark]
        //                         contrast*L + 128*(1-contrast)        [light]
        val cm = if (isDark) {
            val offset = 127f * contrast + 128f
            android.graphics.ColorMatrix(floatArrayOf(
                -0.299f * contrast, -0.587f * contrast, -0.114f * contrast, 0f, offset,
                -0.299f * contrast, -0.587f * contrast, -0.114f * contrast, 0f, offset,
                -0.299f * contrast, -0.587f * contrast, -0.114f * contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            ))
        } else {
            val offset = 128f * (1f - contrast)
            android.graphics.ColorMatrix(floatArrayOf(
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        Log.d("FINORA_OCR", "=== Preprocessing: isDark=$isDark ===")
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun isImageDark(bitmap: android.graphics.Bitmap): Boolean {
        val sampleStep = maxOf(1, minOf(bitmap.width, bitmap.height) / 20)
        var totalLuminance = 0L
        var sampleCount = 0
        for (y in 0 until bitmap.height step sampleStep) {
            for (x in 0 until bitmap.width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                sampleCount++
            }
        }
        return sampleCount > 0 && (totalLuminance / sampleCount) < 128
    }

    private fun fixRupeeSymbolMisreads(text: String): String {
        var result = text

        // ML Kit misreads ₹ as multiple different characters depending on font:
        //   "7" — most common (BHIM, Swiggy dark theme)
        //   "Z" — Zepto, Blinkit thin-stroke font  (Z12, Z514, Z966)
        //   "F" — Blinkit/Zepto (F215, F227, F46)
        //   "T" — Swiggy light amounts (T28.95)
        //   "R" — occasional (R45)
        // Represented as character class: [7ZRFTzrft]
        //
        // Digit group (with optional comma thousands): [0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?

        // Pass 1: standalone amount lines — entire line is "<misread>NNN"
        // Optional leading +/- for lines like "+Z12" (handling charge) or "-74" (discount).
        // The anchor $ ensures "F46.00 FREE" does NOT match (has trailing text).
        // Requires 2+ digits after misread char so "70" (₹ fully dropped, 7 is the tens digit)
        // is NOT misread as ₹0. "770" (₹→7, amount=70) correctly becomes ₹70.
        result = result.replace(
            Regex("""^[+\-]?[7ZRFTHEzrfthe?]([0-9]{2,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)$""", RegexOption.MULTILINE)
        ) { mr -> "₹${mr.groupValues[1]}" }

        // Pass 2: after label keywords, "<misread>NNN" → "₹NNN"
        // Separator is optional so "Grand total7935" and "Grand total  F935" both work.
        result = result.replace(
            Regex(
                """((?:total|paid|charged|amount|payable|subtotal|mrp|bill|price)[:\s]*)[7ZRFTHEzrfthe?]([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        ) { mr -> "${mr.groupValues[1]}₹${mr.groupValues[2]}" }

        // Pass 3: two prices on same line — "₹NNN <misread>NNN" → "₹NNN ₹NNN"
        // Handles: "Total Bill ₹720 Z514", "Item Total ₹941 F726"
        result = result.replace(
            Regex("""(₹[0-9,]+(?:\.[0-9]{1,2})?)\s+[7ZRFTHEzrfthe?]([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""")
        ) { mr -> "${mr.groupValues[1]} ₹${mr.groupValues[2]}" }

        // Pass 4: bare number + misread-prefixed number on same line (amounts column in two-column layouts)
        // "674 Z514" → "₹674 ₹514", "383 T236" → "₹383 ₹236"
        result = result.replace(
            Regex("""^([0-9]{2,5})\s+[7ZRFTHEzrfthe?]([0-9]{2,5}(?:\.[0-9]{1,2})?)$""", RegexOption.MULTILINE)
        ) { mr -> "₹${mr.groupValues[1]} ₹${mr.groupValues[2]}" }

        // Pass 5: two misread-prefixed numbers on same line
        // "Z671 7514" → "₹671 ₹514"
        result = result.replace(
            Regex("""^[7ZRFTHEzrfthe?]([0-9]{2,4})\s+[7ZRFTHEzrfthe?]([0-9]{2,5}(?:\.[0-9]{1,2})?)$""", RegexOption.MULTILINE)
        ) { mr -> "₹${mr.groupValues[1]} ₹${mr.groupValues[2]}" }

        return result
    }

    /**
     * Priority score for an amount found on a given line.
     * Higher = more likely to be the final payable/paid amount.
     * Used to pick the correct amount from receipts with multiple ₹ values
     * (e.g., food delivery: Item total → GST → Grand total → Coupon → Paid).
     */
    private fun getAmountLinePriority(lowerLine: String): Int = when {
        lowerLine.contains("you paid") || lowerLine.contains("amount paid") -> 10
        // "Paid ₹X" but not "Paid to / Paid via / Prepaid"
        lowerLine.contains("paid") &&
                !lowerLine.contains("prepaid") &&
                !lowerLine.contains("paid to") &&
                !lowerLine.contains("paid via") -> 10
        lowerLine.contains("grand total") || lowerLine.contains("bill total") ||
                lowerLine.contains("total bill") -> 9
        lowerLine.contains("order total") || lowerLine.contains("net payable") ||
                lowerLine.contains("total payable") || lowerLine.contains("final amount") -> 8
        lowerLine.contains("total amount") || lowerLine.contains("amount due") ||
                lowerLine.contains("total due") || lowerLine.contains("to pay") -> 7
        lowerLine.contains("item total") -> 4     // More specific — must come before "total"
        lowerLine.contains("total") || lowerLine.contains("subtotal") -> 6
        lowerLine.contains("amount") -> 5
        lowerLine.contains("mrp") || lowerLine.contains("price") -> 2
        else -> 3   // plain currency symbol, no semantic label
    }

    private fun normalizeDateTimeFormat(rawDateTime: String): String {
        if (rawDateTime.isEmpty()) return ""

        try {
            // Pre-process ordinal suffixes: "11th" → "11", "2nd" → "2", "3rd" → "3"
            var processed = rawDateTime.replace(Regex("""(\d+)(?:st|nd|rd|th)\b"""), "$1")
            // Pre-process apostrophe-year: "Oct'25" → "Oct 2025"
            processed = processed.replace(Regex("""([A-Za-z]{3})'(\d{2})\b""")) { mr ->
                val yr = mr.groupValues[2].toInt()
                "${mr.groupValues[1]} ${if (yr <= 50) 2000 + yr else 1900 + yr}"
            }
            // Use preprocessed string for all further parsing
            val rawDateTime = processed

            val dateTimeInputFormats = listOf(
                SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.ENGLISH),
                SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH),
                SimpleDateFormat("h:mm a 'on' d MMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy h:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH),
                SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.ENGLISH),
                SimpleDateFormat("d-MMM-yyyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("d-MMM-yyyy h:mm a", Locale.ENGLISH),
                // Month-first US format (e.g. Swiggy payment date)
                SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH),
                SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH),
                SimpleDateFormat("MMMM d, yyyy, h:mm a", Locale.ENGLISH),
                SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.ENGLISH)
            )
            val dateOnlyInputFormats = listOf(
                SimpleDateFormat("d MMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            )

            val dateTimeOutputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            val dateOnlyOutputFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

            for (inputFormat in dateTimeInputFormats) {
                try {
                    val date = inputFormat.parse(rawDateTime)
                    if (date != null) {
                        return dateTimeOutputFormat.format(date)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            for (inputFormat in dateOnlyInputFormats) {
                try {
                    val date = inputFormat.parse(rawDateTime)
                    if (date != null) {
                        return dateOnlyOutputFormat.format(date)
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
        // Priority 1: inline label — "Description: Monthly subscription", "For: Rent"
        val inlineNotePattern = Regex(
            """^(?:description|for|purpose|narration|remarks|note|message|memo|comment|reason|payment\s+for|particulars)\s*:\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
        for (line in lines) {
            val match = inlineNotePattern.find(line.trim())
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (isValidNoteByPosition(candidate)) {
                    return removeUpiPaymentSuffix(candidate)
                }
            }
        }

        // Priority 2: label on its own line, value on the next line
        val standaloneLabelPattern = Regex(
            """^(?:description|for|purpose|narration|remarks|note|message|memo|comment|reason|particulars)$""",
            RegexOption.IGNORE_CASE
        )
        for (i in lines.indices) {
            if (standaloneLabelPattern.matches(lines[i].trim()) && i + 1 < lines.size) {
                val candidate = lines[i + 1].trim()
                if (isValidNoteByPosition(candidate)) {
                    return removeUpiPaymentSuffix(candidate)
                }
            }
        }

        // Priority 3: positional fallback for UPI receipts (original logic)
        val notePositions = listOf(17, 9)
        for (position in notePositions) {
            if (position < lines.size) {
                val candidateNote = lines[position].trim()
                if (position == 17 && candidateNote.lowercase().contains("powered by")) break
                if (isValidNoteByPosition(candidateNote)) {
                    return removeUpiPaymentSuffix(candidateNote)
                }
            }
        }

        return ""
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
            "reference #",
            "order id",
            "order #",
            "invoice #",
            "delivery address",
            "estimated delivery",
            "track your order"
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
        if (name.length <= 2 || name.length > 60) return false
        if (name.contains("@") || name.equals("VPA", ignoreCase = true)) return false
        // Allow letters, spaces, apostrophes, ampersands, dots, hyphens (brand names)
        if (!name.matches(Regex("[A-Za-z0-9'&.\\s\\-]+"))) return false
        if (name.split("\\s+".toRegex()).size < 1) return false
        val lower = name.lowercase()
        return !lower.contains("bank") && !lower.contains("payment") &&
                !lower.contains("transaction") && !lower.contains("upi") &&
                lower != "total" && lower != "amount" && lower != "order"
    }

    private fun isValidMerchantName(name: String): Boolean {
        if (name.length < 2 || name.length > 60) return false
        if (name.contains("@")) return false
        if (!name.any { it.isLetter() }) return false
        val lower = name.lowercase()
        val blocked = listOf("payment", "transaction", "upi", "amount", "total",
            "n/a", "na", "nil", "null", "none", "order", "invoice", "receipt")
        return blocked.none { lower == it } && !lower.startsWith("xxxxxx")
    }

    private fun cleanCapturedRecipient(raw: String): String {
        return raw.trim()
            .replace(Regex("""\s*\(?\+?\d[\d\s\-]{8,}\)?.*$"""), "")   // trailing phone number
            .replace(Regex("""\s*[-–]\s*UPI.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*via\s+\w+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd(',', ';', '.')
    }

    private fun isStandaloneName(line: String): Boolean {
        // Accept: "Behrouz Biryani", "McDonald's", "H&M", "BigBasket"
        val brandRegex = Regex("""^[A-Z][A-Za-z0-9'&.\-\s]{1,49}$""")
        if (!brandRegex.matches(line)) return false
        if (line.contains("@")) return false
        if (line.length !in 3..50) return false
        val lower = line.lowercase()
        val blocked = listOf("bank", "payment", "transaction", "app", "upi",
            "powered", "interface", "reference", "invoice", "receipt",
            "paid", "sent", "received", "credited", "debited", "total", "amount")
        return blocked.none { lower.contains(it) }
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
                Regex("""T\d{15,25}""") to 5,               // Google Pay transaction IDs
                Regex("""[A-Z0-9]{15,25}""") to 3,
                Regex("""\d{12,20}""") to 2,
                Regex("""[A-Z]{2,4}\d{10,20}""") to 4,
                Regex("""XXXXXX\d{4,}""") to 3,
                Regex("""[A-Z0-9]{10,14}""") to 1,
                // E-commerce order/invoice patterns
                Regex("""OD\d{9,15}""") to 6,               // Flipkart order IDs
                Regex("""\d{3}-\d{7}-\d{7}""") to 6,        // Amazon order IDs
                Regex("""[A-Z]{2,6}-\d{6,15}""") to 3,      // Generic prefixed order IDs
                Regex("""#([A-Z0-9]{6,20})""") to 4         // "Order #ABCD1234"
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
                        context.contains("order id") -> score += 9
                        context.contains("order #") || context.contains("order no") -> score += 7
                        context.contains("invoice") && (context.contains("id") || context.contains("#")) -> score += 7
                        context.contains("booking") && context.contains("id") -> score += 6
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

    private fun normalizeBankInfo(raw: String): String {
        val s = raw.lowercase()
        return when {
            s.contains("google pay") || s.contains("gpay") || s.contains("g pay") -> "Google Pay"
            s.contains("phonepe") || s.contains("phone pe")                        -> "PhonePe"
            s.contains("paytm payments bank")                                       -> "Paytm Payments Bank"
            s.contains("paytm")                                                     -> "Paytm"
            s.contains("amazon pay")                                                -> "Amazon Pay"
            s.contains("mobikwik")                                                  -> "MobiKwik"
            s.contains("freecharge")                                                -> "Freecharge"
            s.contains("airtel")                                                    -> "Airtel Payments Bank"
            s.contains("jio payments")                                              -> "Jio Payments Bank"
            s.contains("samsung pay")                                               -> "Samsung Pay"
            s.contains("cred")                                                      -> "CRED"
            s.contains("icici")                                                     -> "ICICI Bank"
            s.contains("hdfc")                                                      -> "HDFC Bank"
            s.contains("axis")                                                      -> "Axis Bank"
            s.contains("kotak")                                                     -> "Kotak Bank"
            s.contains("state bank") || s.contains("sbi")                          -> "SBI"
            s.contains("yes bank")                                                  -> "Yes Bank"
            s.contains("indusind")                                                  -> "IndusInd Bank"
            s.contains("punjab national") || s.contains("pnb")                     -> "PNB"
            s.contains("canara")                                                    -> "Canara Bank"
            s.contains("bank of baroda")                                            -> "Bank of Baroda"
            s.contains("union bank")                                                -> "Union Bank"
            s.contains("idfc")                                                      -> "IDFC First Bank"
            s.contains("federal bank")                                              -> "Federal Bank"
            s.contains("rbl")                                                       -> "RBL Bank"
            s.contains("au small")                                                  -> "AU Small Finance Bank"
            s.contains("razorpay")                                                  -> "Razorpay"
            s.contains("cashfree")                                                  -> "Cashfree"
            s.contains("payu")                                                      -> "PayU"
            s.contains("ccavenue")                                                  -> "CCAvenue"
            s.contains("billdesk")                                                  -> "BillDesk"
            s.contains("stripe")                                                    -> "Stripe"
            s.contains("paypal")                                                    -> "PayPal"
            s.contains("neft")                                                      -> "NEFT"
            s.contains("imps")                                                      -> "IMPS"
            s.contains("rtgs")                                                      -> "RTGS"
            s.contains("net banking") || s.contains("netbanking")                  -> "Net Banking"
            s.contains("upi")                                                       -> "UPI"
            else -> raw.trim()
        }
    }

    private fun findBankInfo(lines: List<String>): String {
        val bankKeywords = listOf(
            // Indian banks
            "icici bank", "axis bank", "hdfc bank", "kotak bank", "paytm payments bank",
            "state bank of india", "sbi", "yes bank", "indusind bank",
            "punjab national bank", "pnb", "canara bank", "bank of baroda",
            "union bank", "idfc first bank", "federal bank", "rbl bank",
            "au small finance bank",
            // UPI apps and wallets
            "phonepe", "phone pe", "google pay", "gpay", "amazon pay",
            "paytm", "mobikwik", "freecharge", "airtel payments bank",
            "jio payments bank", "samsung pay", "cred",
            // Payment gateways (visible on e-commerce receipts)
            "razorpay", "cashfree", "payu", "ccavenue", "billdesk",
            "stripe", "paypal"
        )
        val avoidLabels = listOf("banking name", "bank name", "from bank", "to bank",
            "powered by", "pay via", "paid via", "payment via")

        for (line in lines) {
            val lowerLine = line.lowercase().trim()

            if (avoidLabels.any { lowerLine == it || lowerLine.contains(it) }) {
                continue
            }

            for (bankKeyword in bankKeywords) {
                if (lowerLine.contains(bankKeyword)) {
                    if (!lowerLine.contains("@") && line.length < 100) {
                        return normalizeBankInfo(line.trim())
                    }
                }
            }
        }

        // Fallback: "Paid via PhonePe", "Payment using GPay", "Pay through Paytm"
        val viaPattern = Regex("""(?:paid|pay|payment)\s+(?:via|using|through|with)\s+(.+)""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = viaPattern.find(line)
            if (match != null) {
                val candidate = match.groupValues[1].trim().trimEnd('.', ',')
                if (candidate.length in 3..50 && !candidate.contains("@")) {
                    return normalizeBankInfo(candidate)
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
        // Capture OCR predictions at the moment results are shown to the user
        if (!instant) {
            isOcrSession = true
            ocrWasEdited = false
            ocrSessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            ocrCapturedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            ocrPredictedAmount = amount
            ocrPredictedRecipient = recipient
            ocrPredictedNote = note
            ocrPredictedDateTime = dateTime
            ocrPredictedTransactionId = transactionId
            ocrPredictedBankInfo = bankInfo
            ocrPredictedCurrency = currency
        }

        statusCard.visibility = View.GONE

        val sheet = if (instant)
            BottomSheetDialog(requireContext(), R.style.BottomSheetDialog_NoAnim)
        else
            BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_transaction_detail_sheet, null)
        sheet.setContentView(sheetView)

        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))

        // Category dot (default until user sets category via edit)
        val defaultCategoryColor = ContextCompat.getColor(
            requireContext(),
            CategoryIconHelper.getIconTintColorRes("cat_other")
        )
        sheetView.findViewById<ImageView>(R.id.detailCategoryIcon).apply {
            setImageResource(R.drawable.shape_dot_solid)
            setColorFilter(defaultCategoryColor)
            applyCategoryDotGlow(defaultCategoryColor)
        }

        // Amount — this preview is the OCR scan-review sheet, always a scanned receipt/expense
        val numericAmount = CurrencyManager.parseAmount(amount)
        val currencySymbol = CurrencyManager.getSymbol(currency)
        sheetView.findViewById<TextView>(R.id.detailAmount).apply {
            text = if (numericAmount > 0) "-$currencySymbol${fmt.format(numericAmount)}" else amount.ifEmpty { "—" }
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_expense))
        }

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
                ocrWasEdited = true
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

            // Capture tracking state before any UI reset (bitmap will be cleared below)
            val shouldTrack = isOcrSession && OcrTrackingManager.isEnabled(requireContext())
            val trackSessionId = ocrSessionId
            val trackCapturedAt = ocrCapturedAt
            val trackBitmap = currentBitmap
            val trackPredicted = mapOf(
                "amount" to ocrPredictedAmount,
                "recipient" to ocrPredictedRecipient,
                "note" to ocrPredictedNote,
                "dateTime" to ocrPredictedDateTime,
                "transactionId" to ocrPredictedTransactionId,
                "bankInfo" to ocrPredictedBankInfo,
                "currency" to ocrPredictedCurrency
            )
            val trackActual = mapOf(
                "amount" to amount,
                "recipient" to recipient,
                "note" to note,
                "dateTime" to dateTime,
                "transactionId" to transactionId,
                "bankInfo" to bankInfo,
                "currency" to currency,
                "category" to category,
                "type" to type
            )
            val trackWasEdited = ocrWasEdited

            // Reset all state
            isOcrSession = false
            ocrSessionId = ""
            pendingAmount = ""; pendingRecipient = ""; pendingNote = ""
            pendingDateTime = ""; pendingTransactionId = ""; pendingBankInfo = ""; pendingCurrency = ""
            imagePreviewCard.visibility = View.GONE
            processButtonCard.visibility = View.GONE
            statusCard.visibility = View.GONE
            currentBitmap = null
            processButton.isEnabled = false

            if (shouldTrack) {
                if (OcrTrackingManager.isFeedbackEnabled(requireContext())) {
                    showOcrFeedbackDialog { feedback ->
                        OcrTrackingManager.saveSession(
                            context = requireContext(),
                            sessionId = trackSessionId,
                            capturedAt = trackCapturedAt,
                            bitmap = trackBitmap,
                            predicted = trackPredicted,
                            actual = trackActual,
                            wasEdited = trackWasEdited,
                            feedback = feedback
                        )
                    }
                } else {
                    OcrTrackingManager.saveSession(
                        context = requireContext(),
                        sessionId = trackSessionId,
                        capturedAt = trackCapturedAt,
                        bitmap = trackBitmap,
                        predicted = trackPredicted,
                        actual = trackActual,
                        wasEdited = trackWasEdited
                    )
                }
            }
        } else {
            Toast.makeText(requireContext(), "Error saving transaction", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOcrFeedbackDialog(onResult: (Map<String, String>?) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.layout_ocr_feedback_dialog, null)
        val ratingGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.ratingToggleGroup)
        val commentInput = dialogView.findViewById<TextInputEditText>(R.id.etFeedbackComment)

        var resultDelivered = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quick Feedback")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                resultDelivered = true
                val rating = when (ratingGroup.checkedButtonId) {
                    R.id.btnRatingCorrect -> "correct"
                    R.id.btnRatingPartial -> "partial"
                    R.id.btnRatingIncorrect -> "incorrect"
                    else -> ""
                }
                onResult(mapOf(
                    "rating" to rating,
                    "comment" to (commentInput.text?.toString()?.trim() ?: "")
                ))
            }
            .setNegativeButton("Skip") { _, _ ->
                resultDelivered = true
                onResult(null)
            }
            .setOnDismissListener { if (!resultDelivered) onResult(null) }
            .create()

        dialog.applyGlassBlur()

        // Restyle the native action buttons to match the glass dialog: gradient pill for
        // Submit, plain muted text for Skip. Visual-only — no submission logic touched.
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
                setBackgroundResource(R.drawable.balance_card_gradient)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_primary))
                val hPad = (16 * resources.displayMetrics.density).toInt()
                val vPad = (8 * resources.displayMetrics.density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface_muted))
            }
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}
