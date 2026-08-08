package com.example.expensetracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.expensetracker.ui.common.themeColor
import com.example.expensetracker.ui.common.applyGlassBlur
import com.example.expensetracker.ui.common.applyVividGlow
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class EditPaymentActivity : AppCompatActivity() {

    private lateinit var amountEditText: EditText
    private lateinit var amountCurrencySymbol: TextView
    private lateinit var recipientEditText: EditText
    private lateinit var dateTimeEditText: EditText
    private lateinit var transactionIdEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var bankEditText: Spinner
    private lateinit var categorySelector: LinearLayout
    private lateinit var categorySelectorIconBg: View
    private lateinit var categorySelectorEmoji: ImageView
    private lateinit var categorySelectorName: TextView
    private lateinit var currencyButton: TextView
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageButton

    private lateinit var categoryManager: CategoryManager
    private var categories = listOf<Category>()
    private var selectedCategoryId = "cat_other"
    private var selectedCurrency = "INR"
    private var selectedType = "expense" // "expense" or "income"
    private lateinit var typeExpenseButton: MaterialButton
    private lateinit var typeIncomeButton: MaterialButton

    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedTime: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark
        setContentView(R.layout.activity_edit_payment)

        // Apply status bar height to header and nav bar height to footer
        val dp = resources.displayMetrics.density
        val header = findViewById<LinearLayout>(R.id.editHeader)
        val footer = findViewById<LinearLayout>(R.id.editFooter)
        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBars.top + (16 * dp).toInt())
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(footer) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBars.bottom + (20 * dp).toInt())
            insets
        }

        categoryManager = CategoryManager(this)
        categoryManager.initializeDefaultCategories()

        val isEditingExisting = intent.hasExtra("editingId")
        findViewById<TextView>(R.id.editScreenTitle).text =
            if (isEditingExisting) "Edit Transaction" else "Add Transaction"
        findViewById<TextView>(R.id.editScreenSubtitle).text =
            if (isEditingExisting) "Update transaction details" else "Enter transaction details"

        showReceiptPreviewIfPresent()

        initializeViews()
        loadCategories()
        populateFields()
        setupButtonListeners()
        setupBackPressHandler()
    }

    // Shows the scanned-receipt thumbnail above the form when arriving from the Upload
    // Receipt flow (AddFragment passes the picked image's Uri) — hidden for manual entry
    // and for editing an existing saved transaction, matching Stitch's "Upload Receipt -
    // Review Details" screen.
    private fun showReceiptPreviewIfPresent() {
        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString.isNullOrEmpty()) return

        try {
            val uri = Uri.parse(imageUriString)
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            findViewById<ImageView>(R.id.receiptPreviewImage).setImageBitmap(bitmap)
            findViewById<View>(R.id.receiptPreviewHeader).visibility = View.VISIBLE
        } catch (e: Exception) {
            // Image failed to load — the form still works fine without the preview, so just
            // leave receiptPreviewHeader hidden rather than blocking the review flow.
        }
    }

    private fun initializeViews() {
        amountEditText = findViewById(R.id.amountEditText)
        amountCurrencySymbol = findViewById(R.id.amountCurrencySymbol)
        recipientEditText = findViewById(R.id.recipientEditText)
        dateTimeEditText = findViewById(R.id.dateTimeEditText)
        transactionIdEditText = findViewById(R.id.transactionIdEditText)
        noteEditText = findViewById(R.id.noteEditText)
        bankEditText = findViewById(R.id.bankEditText)
        categorySelector = findViewById(R.id.categorySelector)
        categorySelectorIconBg = findViewById(R.id.categorySelectorIconBg)
        categorySelectorEmoji = findViewById(R.id.categorySelectorEmoji)
        categorySelectorName = findViewById(R.id.categorySelectorName)
        currencyButton = findViewById(R.id.currencyButton)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)
        typeExpenseButton = findViewById(R.id.typeExpenseButton)
        typeIncomeButton = findViewById(R.id.typeIncomeButton)

        selectedCurrency = intent.getStringExtra("currency") ?: CurrencyManager.getDefault(this)
        updateCurrencyDisplay()
        findViewById<LinearLayout>(R.id.currencyRow).setOnClickListener { showCurrencyPicker() }

        selectedType = intent.getStringExtra("type") ?: "expense"
        updateTypeButtons()
        typeExpenseButton.setOnClickListener { selectedType = "expense"; updateTypeButtons() }
        typeIncomeButton.setOnClickListener { selectedType = "income"; updateTypeButtons() }

        val paymentMethods = arrayOf("Google Pay", "PhonePe", "ICICI Bank", "HDFC Bank", "Paytm", "Other")
        val spinnerAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, paymentMethods)
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        bankEditText.adapter = spinnerAdapter

        backButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
            overridePendingTransition(0, 0)
        }

        dateTimeEditText.isFocusable = false
        dateTimeEditText.isClickable = true
        dateTimeEditText.setOnClickListener { showDateTimePicker() }

        categorySelector.setOnClickListener { showCategoryPicker() }
    }

    private fun loadCategories() {
        categories = categoryManager.getAllCategories()
        updateCategorySelectorDisplay()
    }

    private fun updateCategorySelectorDisplay() {
        val selected = categories.find { it.id == selectedCategoryId }
        val categoryId = selected?.id ?: "cat_other"
        val tint = ContextCompat.getColor(this, CategoryIconHelper.getIconTintColorRes(categoryId))

        categorySelectorEmoji.setImageResource(CategoryIconHelper.getIconResId(categoryId))
        categorySelectorEmoji.setColorFilter(Color.WHITE)
        (categorySelectorIconBg.background as? GradientDrawable)?.setColor(tint)
        categorySelectorIconBg.applyVividGlow(tint, cornerRadiusDp = 12f)
        categorySelectorEmoji.elevation = categorySelectorIconBg.elevation + 1f

        if (selected != null) {
            categorySelectorName.text = selected.name
            categorySelectorName.setTextColor(themeColor(R.attr.colorOnSurface))
        } else {
            categorySelectorName.text = "Select category"
            categorySelectorName.setTextColor(themeColor(R.attr.colorOnSurfaceFaint))
        }
    }

    private fun showCategoryPicker() {
        val sheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.layout_category_picker_sheet, null)
        sheet.setContentView(sheetView)

        val recycler = sheetView.findViewById<RecyclerView>(R.id.categoryPickerRecyclerView)
        recycler.layoutManager = GridLayoutManager(this, 3)
        val adapter = CategoryPickerAdapter(categories, selectedCategoryId) { category ->
            selectedCategoryId = category.id
            updateCategorySelectorDisplay()
            sheet.dismiss()
        }
        recycler.adapter = adapter

        sheetView.findViewById<EditText>(R.id.categoryPickerSearchInput).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        sheetView.findViewById<ImageButton>(R.id.closePickerButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.createCategoryButton).setOnClickListener {
            sheet.dismiss()
            showAddNewCategoryDialog()
        }

        sheet.applyGlassBlur()
        sheet.show()
    }

    private fun showAddNewCategoryDialog() {
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = sheetView.findViewById<EditText>(R.id.categoryNameInput)
        val iconPickerGrid = sheetView.findViewById<GridLayout>(R.id.iconPickerGrid)

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(sheetView)

        sheetView.findViewById<ImageButton>(R.id.closeButton).setOnClickListener { sheet.dismiss() }
        sheetView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { sheet.dismiss() }

        val iconOptions = CategoryIconHelper.allIconKeys.map { key -> key to CategoryIconHelper.getIconResId(key) }
        var selectedIconKey = iconOptions[0].first
        val density = resources.displayMetrics.density
        val cellSize = (48 * density).toInt()
        val cellMargin = (4 * density).toInt()
        val iconPadding = (12 * density).toInt()
        val iconViews = mutableListOf<Pair<FrameLayout, ImageView>>()

        // Each swatch is filled with its own category tint color (CategoryIconHelper); a ring
        // border indicates the current selection instead of swapping fill color.
        fun makeBackground(key: String, selected: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(this@EditPaymentActivity, CategoryIconHelper.getIconTintColorRes(key)))
            if (selected) {
                setStroke((2.5f * density).toInt(), this@EditPaymentActivity.themeColor(R.attr.colorOnSurface))
            }
        }
        fun updateSelection(selectedIdx: Int) {
            iconViews.forEachIndexed { idx, (frame, img) ->
                val key = iconOptions[idx].first
                frame.background = makeBackground(key, idx == selectedIdx)
                img.setColorFilter(Color.WHITE)
            }
        }
        iconOptions.forEachIndexed { idx, (key, resId) ->
            val frame = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED)).apply { width = cellSize; height = cellSize; setMargins(cellMargin, cellMargin, cellMargin, cellMargin) }
                background = makeBackground(key, false)
            }
            val img = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                setImageResource(resId); scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                setColorFilter(Color.WHITE)
            }
            frame.addView(img)
            frame.setOnClickListener { selectedIconKey = key; updateSelection(idx) }
            iconViews.add(frame to img)
            iconPickerGrid.addView(frame)
        }
        updateSelection(0)

        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).setOnClickListener {
            val name = categoryNameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val success = categoryManager.addCategory(name, selectedIconKey)
                if (success) {
                    Toast.makeText(this, "Category added!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    loadCategories()
                    val newCat = categories.lastOrNull { !it.isPredefined }
                    if (newCat != null) {
                        selectedCategoryId = newCat.id
                        updateCategorySelectorDisplay()
                    }
                } else {
                    Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a category name", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.applyGlassBlur()
        sheet.show()
    }

    private fun populateFields() {
        val amount = intent.getStringExtra("amount") ?: ""
        val recipient = intent.getStringExtra("recipient") ?: ""
        val dateTime = intent.getStringExtra("dateTime") ?: ""
        val transactionId = intent.getStringExtra("transactionId") ?: ""
        val note = intent.getStringExtra("note") ?: ""
        val bankInfo = intent.getStringExtra("bankInfo") ?: ""
        selectedCategoryId = intent.getStringExtra("category") ?: "cat_other"
        // currency already loaded in initializeViews(); button already shows correct symbol

        amountEditText.setText(amount.replace("₹", "").replace(",", "").trim())
        recipientEditText.setText(recipient)
        noteEditText.setText(note)
        transactionIdEditText.setText(transactionId)

        val paymentMethods = arrayOf("Google Pay", "PhonePe", "ICICI Bank", "HDFC Bank", "Paytm", "Other")
        val paymentIndex = paymentMethods.indexOfFirst { it.equals(bankInfo, ignoreCase = true) }
        bankEditText.setSelection(if (paymentIndex != -1) paymentIndex else paymentMethods.size - 1)

        updateCategorySelectorDisplay()

        if (dateTime.isNotEmpty()) {
            dateTimeEditText.setText(dateTime)
            parseDateTimeString(dateTime)
        } else {
            updateDateTimeField()
        }
    }

    private fun setupButtonListeners() {
        saveButton.setOnClickListener { saveAndReturn() }
    }

    private fun saveAndReturn() {
        val updatedAmount = amountEditText.text.toString().trim()
            .replace("₹", "").replace(",", "").trim()
        val updatedRecipient = recipientEditText.text.toString().trim()
        val updatedDateTime = dateTimeEditText.text.toString().trim()
        val updatedTransactionId = transactionIdEditText.text.toString().trim()
        val updatedNote = noteEditText.text.toString().trim()
        val updatedBankInfo = bankEditText.selectedItem.toString()
        val updatedCategory = selectedCategoryId

        if (updatedAmount.isEmpty()) {
            amountEditText.error = "Amount is required"
            amountEditText.requestFocus()
            return
        }
        val numericAmount = updatedAmount.toDoubleOrNull()
        if (numericAmount == null || numericAmount <= 0) {
            amountEditText.error = "Enter a valid amount"
            amountEditText.requestFocus()
            return
        }
        if (updatedRecipient.isBlank()) {
            recipientEditText.error = "Recipient name is required"
            recipientEditText.requestFocus()
            return
        }

        val resultIntent = Intent().apply {
            putExtra("amount", updatedAmount)
            putExtra("recipient", updatedRecipient)
            putExtra("dateTime", updatedDateTime)
            putExtra("transactionId", updatedTransactionId)
            putExtra("note", updatedNote)
            putExtra("bankInfo", updatedBankInfo)
            putExtra("category", updatedCategory)
            putExtra("currency", selectedCurrency)
            putExtra("type", selectedType)
            val editingId = intent.getStringExtra("editingId")
            if (editingId != null) putExtra("editingId", editingId)
        }

        Toast.makeText(this, "Payment details saved!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
                overridePendingTransition(0, 0)
            }
        })
    }

    private fun updateTypeButtons() {
        val activeText = resources.getColor(android.R.color.white, null)
        val inactiveText = themeColor(R.attr.colorOnSurfaceMuted)
        val expenseGlow = resources.getColor(R.color.color_expense, null)
        val incomeGlow = resources.getColor(R.color.color_income, null)
        val density = resources.displayMetrics.density
        val bleedPx = 14f * density
        val yOffsetPx = 4f * density // matches Stitch's box-shadow "0 4px 15px" y-offset

        // The glow is painted by GlowPillDrawable using non-overlapping annulus rings, not
        // View.applyVividGlow's elevation/outline-shadow tinting (didn't render at all in
        // testing) or a stacked-flat-layer XML drawable (visibly banded) — see its kdoc.
        fun activate(button: MaterialButton, fillRes: Int, glowColor: Int) {
            val fill = ContextCompat.getDrawable(this, fillRes)!!
            button.backgroundTintList = null
            button.background = com.example.expensetracker.ui.common.GlowPillDrawable(glowColor, bleedPx, yOffsetPx, fill)
            button.setTextColor(activeText)
        }
        fun deactivate(button: MaterialButton) {
            button.background = null
            button.setTextColor(inactiveText)
        }

        if (selectedType == "expense") {
            activate(typeExpenseButton, R.drawable.pill_toggle_active_expense, expenseGlow)
            deactivate(typeIncomeButton)
        } else {
            activate(typeIncomeButton, R.drawable.pill_toggle_active_income, incomeGlow)
            deactivate(typeExpenseButton)
        }
    }

    private fun showCurrencyPicker() {
        val currencies = CurrencyManager.CURRENCIES
        val items = currencies.map { "${it.symbol}  ${it.code} — ${it.name}" }.toTypedArray()
        val currentIdx = currencies.indexOfFirst { it.code == selectedCurrency }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(items, currentIdx) { dialog, idx ->
                selectedCurrency = currencies[idx].code
                updateCurrencyDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatCurrencyLabel(code: String): String {
        val info = CurrencyManager.getInfo(code) ?: return code
        return "${info.code} (${info.symbol})"
    }

    private fun updateCurrencyDisplay() {
        currencyButton.text = formatCurrencyLabel(selectedCurrency)
        amountCurrencySymbol.text = CurrencyManager.getSymbol(selectedCurrency)
    }

    private fun showDateTimePicker() { showDatePicker() }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate.set(year, month, day)
            showTimePicker()
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(this, { _, hour, minute ->
            selectedTime.set(Calendar.HOUR_OF_DAY, hour)
            selectedTime.set(Calendar.MINUTE, minute)
            updateDateTimeField()
        }, selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE), false).show()
    }

    private fun updateDateTimeField() {
        val calendar = Calendar.getInstance()
        calendar.set(
            selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH),
            selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE)
        )
        dateTimeEditText.setText(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(calendar.time))
    }

    private fun parseDateTimeString(dateTimeString: String) {
        try {
            val parsed = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(dateTimeString)
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                selectedDate.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                selectedDate.set(Calendar.MONTH, cal.get(Calendar.MONTH))
                selectedDate.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                selectedTime.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                selectedTime.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
            }
        } catch (e: Exception) { /* keep defaults */ }
    }

    // Adapter for the category picker grid (single-select, with local name search/filter)
    private class CategoryPickerAdapter(
        private val allCategories: List<Category>,
        private val selectedId: String,
        private val onSelect: (Category) -> Unit
    ) : RecyclerView.Adapter<CategoryPickerAdapter.ViewHolder>() {

        private var categories: List<Category> = allCategories

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.categoryPickerCard)
            val iconCard: MaterialCardView = itemView.findViewById(R.id.categoryIconCard)
            val icon: ImageView = itemView.findViewById(R.id.categoryPickerIcon)
            val name: TextView = itemView.findViewById(R.id.categoryPickerName)
        }

        fun filter(query: String) {
            categories = if (query.isBlank()) allCategories
                else allCategories.filter { it.name.contains(query, ignoreCase = true) }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val isSelected = category.id == selectedId
            val ctx = holder.itemView.context
            val tint = ContextCompat.getColor(ctx, CategoryIconHelper.getIconTintColorRes(category.id))

            holder.icon.setImageResource(CategoryIconHelper.getIconResId(category.id))
            holder.name.text = category.name

            if (isSelected) {
                holder.card.strokeColor = tint
                holder.card.strokeWidth = (2 * ctx.resources.displayMetrics.density).toInt()
                holder.card.setCardBackgroundColor(ctx.themeColor(R.attr.colorGlassFillL3))
                holder.iconCard.setCardBackgroundColor(tint)
                holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.color_on_primary)
                )
                holder.name.setTextColor(ctx.themeColor(R.attr.colorOnSurface))
            } else {
                holder.card.strokeColor = ctx.themeColor(R.attr.colorGlassBorder)
                holder.card.strokeWidth = (1 * ctx.resources.displayMetrics.density).toInt()
                holder.card.setCardBackgroundColor(ctx.themeColor(R.attr.colorGlassFillL2))
                holder.iconCard.setCardBackgroundColor(withAlpha(tint, 0x26))
                holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
                holder.name.setTextColor(ctx.themeColor(R.attr.colorOnSurface))
            }

            holder.card.setOnClickListener { onSelect(category) }
        }

        override fun getItemCount() = categories.size

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
