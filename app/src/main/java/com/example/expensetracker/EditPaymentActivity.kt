package com.example.expensetracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class EditPaymentActivity : AppCompatActivity() {

    private lateinit var amountEditText: EditText
    private lateinit var recipientEditText: EditText
    private lateinit var dateTimeEditText: EditText
    private lateinit var transactionIdEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var bankEditText: Spinner
    private lateinit var categorySelector: LinearLayout
    private lateinit var categorySelectorEmoji: ImageView
    private lateinit var categorySelectorName: TextView
    private lateinit var currencyButton: TextView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
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
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

        initializeViews()
        loadCategories()
        populateFields()
        setupButtonListeners()
        setupBackPressHandler()
    }

    private fun initializeViews() {
        amountEditText = findViewById(R.id.amountEditText)
        recipientEditText = findViewById(R.id.recipientEditText)
        dateTimeEditText = findViewById(R.id.dateTimeEditText)
        transactionIdEditText = findViewById(R.id.transactionIdEditText)
        noteEditText = findViewById(R.id.noteEditText)
        bankEditText = findViewById(R.id.bankEditText)
        categorySelector = findViewById(R.id.categorySelector)
        categorySelectorEmoji = findViewById(R.id.categorySelectorEmoji)
        categorySelectorName = findViewById(R.id.categorySelectorName)
        currencyButton = findViewById(R.id.currencyButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        backButton = findViewById(R.id.backButton)
        typeExpenseButton = findViewById(R.id.typeExpenseButton)
        typeIncomeButton = findViewById(R.id.typeIncomeButton)

        selectedCurrency = intent.getStringExtra("currency") ?: CurrencyManager.getDefault(this)
        currencyButton.text = CurrencyManager.getSymbol(selectedCurrency)
        currencyButton.setOnClickListener { showCurrencyPicker() }

        selectedType = intent.getStringExtra("type") ?: "expense"
        updateTypeButtons()
        typeExpenseButton.setOnClickListener { selectedType = "expense"; updateTypeButtons() }
        typeIncomeButton.setOnClickListener { selectedType = "income"; updateTypeButtons() }

        val paymentMethods = arrayOf("Google Pay", "PhonePe", "ICICI Bank", "HDFC Bank", "Paytm", "Other")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, paymentMethods)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
        if (selected != null) {
            categorySelectorEmoji.setImageResource(CategoryIconHelper.getIconResId(selected.id))
            categorySelectorName.text = selected.name
            categorySelectorName.setTextColor(getColor(android.R.color.black))
        } else {
            categorySelectorEmoji.setImageResource(CategoryIconHelper.getIconResId("cat_other"))
            categorySelectorName.text = "Select category"
            categorySelectorName.setTextColor(resources.getColor(R.color.text_secondary_light, null))
        }
    }

    private fun showCategoryPicker() {
        val sheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.layout_category_picker_sheet, null)
        sheet.setContentView(sheetView)

        val recycler = sheetView.findViewById<RecyclerView>(R.id.categoryPickerRecyclerView)
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = CategoryPickerAdapter(categories, selectedCategoryId) { category ->
            selectedCategoryId = category.id
            updateCategorySelectorDisplay()
            sheet.dismiss()
        }

        sheetView.findViewById<ImageButton>(R.id.closePickerButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.createCategoryButton).setOnClickListener {
            sheet.dismiss()
            showAddNewCategoryDialog()
        }

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

        fun makeBackground(filled: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * density
            setColor(if (filled) Color.parseColor("#6B5DD3") else Color.parseColor("#EDE9FE"))
        }
        fun updateSelection(selectedIdx: Int) {
            iconViews.forEachIndexed { idx, (frame, img) ->
                val sel = idx == selectedIdx
                frame.background = makeBackground(sel)
                img.setColorFilter(if (sel) Color.WHITE else Color.parseColor("#6B5DD3"))
            }
        }
        iconOptions.forEachIndexed { idx, (key, resId) ->
            val frame = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0; height = cellSize; setMargins(cellMargin, cellMargin, cellMargin, cellMargin) }
                background = makeBackground(false)
            }
            val img = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                setImageResource(resId); scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                setColorFilter(Color.parseColor("#6B5DD3"))
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
        val paymentIndex = paymentMethods.indexOf(bankInfo)
        if (paymentIndex != -1) bankEditText.setSelection(paymentIndex)

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
        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
            overridePendingTransition(0, 0)
        }
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
        val activeColor = resources.getColor(R.color.primary_indigo, null)
        val inactiveColor = android.graphics.Color.TRANSPARENT
        val activeText = resources.getColor(android.R.color.white, null)
        val inactiveText = resources.getColor(R.color.text_secondary_light, null)
        if (selectedType == "expense") {
            typeExpenseButton.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
            typeExpenseButton.setTextColor(activeText)
            typeIncomeButton.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
            typeIncomeButton.setTextColor(inactiveText)
        } else {
            typeIncomeButton.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
            typeIncomeButton.setTextColor(activeText)
            typeExpenseButton.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
            typeExpenseButton.setTextColor(inactiveText)
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
                currencyButton.text = CurrencyManager.getSymbol(selectedCurrency)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    // Adapter for the category picker 2-column grid
    private class CategoryPickerAdapter(
        private val categories: List<Category>,
        private val selectedId: String,
        private val onSelect: (Category) -> Unit
    ) : RecyclerView.Adapter<CategoryPickerAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.categoryPickerCard)
            val iconCard: MaterialCardView = itemView.findViewById(R.id.categoryIconCard)
            val icon: ImageView = itemView.findViewById(R.id.categoryPickerIcon)
            val name: TextView = itemView.findViewById(R.id.categoryPickerName)
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

            holder.icon.setImageResource(CategoryIconHelper.getIconResId(category.id))
            holder.name.text = category.name

            if (isSelected) {
                holder.card.strokeColor = ctx.getColor(R.color.primary_indigo)
                holder.card.setCardBackgroundColor(0x1A6B5DD3.toInt())
                holder.iconCard.setCardBackgroundColor(ctx.getColor(R.color.primary_indigo))
                holder.name.setTextColor(ctx.getColor(R.color.primary_indigo))
            } else {
                holder.card.strokeColor = 0x33000000
                holder.card.setCardBackgroundColor(ctx.getColor(android.R.color.white))
                holder.iconCard.setCardBackgroundColor(0xFFEEF2FF.toInt())
                holder.name.setTextColor(ctx.getColor(android.R.color.black))
            }

            holder.card.setOnClickListener { onSelect(category) }
        }

        override fun getItemCount() = categories.size
    }
}
