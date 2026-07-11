package com.example.expensetracker

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.data.CategoryGuesser
import com.example.expensetracker.data.GpayPdfParser
import com.example.expensetracker.data.ParsedTransaction
import com.example.expensetracker.data.SbiPdfParser
import com.example.expensetracker.data.SbiXlsxParser
import com.example.expensetracker.data.StatementFormat
import com.example.expensetracker.data.StatementType
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

class StatementImportActivity : AppCompatActivity() {

    companion object {
        private const val PICK_FILE_REQUEST = 2001
        private const val TAG = "StatementImportActivity"
        private const val STATE_UPLOAD  = 0
        private const val STATE_PARSING = 1
        private const val STATE_REVIEW  = 2
        private const val STATE_ERROR   = 3
    }

    private lateinit var csvManager: CSVManager

    // UPLOAD state
    private lateinit var importButton: MaterialButton
    private lateinit var selectedFileCard: MaterialCardView
    private lateinit var uploadZone: View
    private lateinit var selectedFileName: TextView
    private lateinit var selectedFileSize: TextView
    private lateinit var uploadBottomContent: View

    // PARSING state
    private lateinit var parsingStatusText: TextView

    // REVIEW state
    private lateinit var reviewList: RecyclerView
    private lateinit var reviewSummaryText: TextView
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var reviewBottomContent: View
    private lateinit var selectedCountText: TextView
    private lateinit var confirmImportButton: MaterialButton

    // ERROR state
    private lateinit var errorMessageText: TextView

    private lateinit var stateFlipper: ViewFlipper

    private var selectedFileUri: Uri? = null
    private var parsedTransactions: List<ParsedTransaction> = emptyList()
    private lateinit var reviewAdapter: ImportReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark

        setContentView(R.layout.activity_statement_import)
        csvManager = CSVManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBarLayout)) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomBar)) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom + 16)
            insets
        }

        bindViews()
        wireListeners()
        showState(STATE_UPLOAD)
    }

    private fun bindViews() {
        stateFlipper        = findViewById(R.id.stateFlipper)
        importButton        = findViewById(R.id.importButton)
        selectedFileCard    = findViewById(R.id.selectedFileCard)
        uploadZone          = findViewById(R.id.uploadZone)
        selectedFileName    = findViewById(R.id.selectedFileName)
        selectedFileSize    = findViewById(R.id.selectedFileSize)
        uploadBottomContent = findViewById(R.id.uploadBottomContent)
        parsingStatusText   = findViewById(R.id.parsingStatusText)
        reviewList          = findViewById(R.id.reviewList)
        reviewSummaryText   = findViewById(R.id.reviewSummaryText)
        selectAllCheckbox   = findViewById(R.id.selectAllCheckbox)
        reviewBottomContent = findViewById(R.id.reviewBottomContent)
        selectedCountText   = findViewById(R.id.selectedCountText)
        confirmImportButton = findViewById(R.id.confirmImportButton)
        errorMessageText    = findViewById(R.id.errorMessageText)
    }

    private fun wireListeners() {
        uploadZone.setOnClickListener { pickFile() }
        findViewById<MaterialButton>(R.id.browseFilesButton).setOnClickListener { pickFile() }
        findViewById<MaterialButton>(R.id.removeFileButton).setOnClickListener { clearSelectedFile() }

        importButton.setOnClickListener {
            val uri = selectedFileUri ?: return@setOnClickListener
            parseStatementAsync(uri)
        }

        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (::reviewAdapter.isInitialized) {
                reviewAdapter.toggleSelectAll(isChecked)
                updateImportButtonLabel()
            }
        }

        confirmImportButton.setOnClickListener { importSelected() }

        findViewById<MaterialButton>(R.id.tryAgainButton).setOnClickListener {
            clearSelectedFile()
            showState(STATE_UPLOAD)
        }
    }

    // ── State transitions ───────────────────────────────────────────────────────

    private fun showState(state: Int) {
        stateFlipper.displayedChild = state
        uploadBottomContent.visibility  = if (state == STATE_UPLOAD)  View.VISIBLE else View.GONE
        reviewBottomContent.visibility  = if (state == STATE_REVIEW)  View.VISIBLE else View.GONE
    }

    // ── File picking ────────────────────────────────────────────────────────────

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/octet-stream"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "Select bank statement"), PICK_FILE_REQUEST)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedFileUri = uri
            showSelectedFile(uri)
        }
    }

    private fun showSelectedFile(uri: Uri) {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "statement"
        val size = contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val bytes = pfd.statSize
            when {
                bytes < 1024        -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else                -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        } ?: ""

        selectedFileName.text = name
        selectedFileSize.text = size
        selectedFileCard.visibility = View.VISIBLE
        uploadZone.visibility = View.GONE
        importButton.isEnabled = true
        importButton.alpha = 1f
    }

    private fun clearSelectedFile() {
        selectedFileUri = null
        selectedFileCard.visibility = View.GONE
        uploadZone.visibility = View.VISIBLE
        importButton.isEnabled = false
        importButton.alpha = 0.5f
    }

    // ── Parsing ─────────────────────────────────────────────────────────────────

    private fun parseStatementAsync(uri: Uri) {
        showState(STATE_PARSING)
        parsingStatusText.text = "Reading statement..."

        Thread {
            try {
                val format = StatementFormat.detect(this, uri)

                if (format == StatementType.UNKNOWN) {
                    runOnUiThread {
                        showError("Format not supported.\n\nPlease use an SBI bank statement (PDF or XLSX) or a Google Pay PDF statement.")
                    }
                    return@Thread
                }

                val parser = when (format) {
                    StatementType.SBI_XLSX -> SbiXlsxParser()
                    StatementType.SBI_PDF  -> SbiPdfParser()
                    StatementType.GPAY_PDF -> GpayPdfParser()
                    else -> { runOnUiThread { showError("Unrecognized format") }; return@Thread }
                }

                val transactions = parser.parse(this, uri)
                Log.d(TAG, "Parsed ${transactions.size} transactions (format=$format)")

                runOnUiThread {
                    if (transactions.isEmpty()) {
                        showError("No transactions found.\n\nMake sure the file is a valid bank statement and not password-protected.")
                    } else {
                        showReview(transactions)
                    }
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during parsing", e)
                runOnUiThread { showError("File too large to process.\nTry a statement with a shorter date range.") }
            } catch (e: Exception) {
                Log.e(TAG, "Parsing failed", e)
                runOnUiThread { showError("Could not read file:\n${e.localizedMessage}") }
            }
        }.start()
    }

    private fun showReview(transactions: List<ParsedTransaction>) {
        parsedTransactions = transactions

        reviewAdapter = ImportReviewAdapter(transactions) { selectedCount ->
            updateImportButtonLabel(selectedCount)
            // Sync "Select All" checkbox without triggering its own listener
            selectAllCheckbox.setOnCheckedChangeListener(null)
            selectAllCheckbox.isChecked = selectedCount == transactions.size
            selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
                reviewAdapter.toggleSelectAll(isChecked)
                updateImportButtonLabel()
            }
        }
        reviewList.layoutManager = LinearLayoutManager(this)
        reviewList.adapter = reviewAdapter

        reviewSummaryText.text = "${transactions.size} transactions found"
        selectAllCheckbox.isChecked = true
        updateImportButtonLabel(transactions.size)

        showState(STATE_REVIEW)
    }

    private fun updateImportButtonLabel(count: Int = if (::reviewAdapter.isInitialized) reviewAdapter.selectedCount() else 0) {
        selectedCountText.text = "$count of ${parsedTransactions.size} selected"
        if (count > 0) {
            confirmImportButton.text = "Import $count transaction${if (count != 1) "s" else ""}"
            confirmImportButton.isEnabled = true
            confirmImportButton.alpha = 1f
        } else {
            confirmImportButton.text = "Select transactions to import"
            confirmImportButton.isEnabled = false
            confirmImportButton.alpha = 0.5f
        }
    }

    private fun showError(message: String) {
        errorMessageText.text = message
        showState(STATE_ERROR)
    }

    // ── Import ──────────────────────────────────────────────────────────────────

    private fun importSelected() {
        val selected = reviewAdapter.getSelectedTransactions()
        if (selected.isEmpty()) return

        showState(STATE_PARSING)
        parsingStatusText.text = "Importing ${selected.size} transactions..."

        Thread {
            // Build existing txnId set for dedup check
            val existingIds = csvManager.getAllTransactions()
                .map { it.transactionId }
                .filter { it.isNotEmpty() }
                .toHashSet()

            var imported = 0
            var skipped  = 0

            selected.forEach { parsed ->
                if (parsed.transactionId.isNotEmpty() && parsed.transactionId in existingIds) {
                    skipped++
                    return@forEach
                }
                val tx = PaymentTransaction(
                    amount        = parsed.amount,
                    recipient     = parsed.recipient,
                    note          = parsed.note,
                    dateTime      = parsed.date,
                    transactionId = parsed.transactionId,
                    bankInfo      = parsed.bankInfo,
                    category      = parsed.category,
                    type          = parsed.type
                )
                if (csvManager.saveTransaction(tx)) imported++
            }

            val imp = imported; val sk = skipped
            runOnUiThread {
                val msg = buildString {
                    append("Imported $imp transaction${if (imp != 1) "s" else ""}")
                    if (sk > 0) append(" · $sk already existed")
                }
                Snackbar.make(
                    findViewById(android.R.id.content),
                    msg,
                    Snackbar.LENGTH_LONG
                ).show()
                finish()
            }
        }.start()
    }

    // ── ImportReviewAdapter ─────────────────────────────────────────────────────

    inner class ImportReviewAdapter(
        private val items: List<ParsedTransaction>,
        private val onSelectionChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<ImportReviewAdapter.VH>() {

        private val selectedPositions: MutableSet<Int> = (0 until items.size).toMutableSet()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox:     CheckBox   = view.findViewById(R.id.importCheckbox)
            val categoryIcon: ImageView  = view.findViewById(R.id.importCategoryIcon)
            val merchant:     TextView   = view.findViewById(R.id.importMerchant)
            val note:         TextView   = view.findViewById(R.id.importNote)
            val date:         TextView   = view.findViewById(R.id.importDate)
            val amount:       TextView   = view.findViewById(R.id.importAmount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_statement_transaction, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item       = items[position]
            val isSelected = position in selectedPositions

            holder.checkbox.isChecked = isSelected
            holder.merchant.text      = item.recipient.ifBlank { "(unknown)" }
            holder.date.text          = item.date
            holder.categoryIcon.setImageResource(CategoryIconHelper.getIconResId(item.category))

            if (item.note.isNotBlank() && item.note != item.recipient) {
                holder.note.text       = item.note
                holder.note.visibility = View.VISIBLE
            } else {
                holder.note.visibility = View.GONE
            }

            val sign = if (item.type == "income") "+" else "-"
            holder.amount.text     = "$sign₹${item.amount}"
            holder.amount.setTextColor(
                if (item.type == "income") Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            )

            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                if (pos in selectedPositions) selectedPositions.remove(pos) else selectedPositions.add(pos)
                notifyItemChanged(pos)
                onSelectionChanged(selectedPositions.size)
            }
        }

        override fun getItemCount() = items.size

        fun selectedCount() = selectedPositions.size

        fun getSelectedTransactions() = items.filterIndexed { i, _ -> i in selectedPositions }

        fun toggleSelectAll(selectAll: Boolean) {
            if (selectAll) selectedPositions.addAll(0 until items.size) else selectedPositions.clear()
            notifyDataSetChanged()
            onSelectionChanged(selectedPositions.size)
        }
    }
}
