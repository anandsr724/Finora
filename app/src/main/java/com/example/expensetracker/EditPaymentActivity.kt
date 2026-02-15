package com.example.expensetracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class EditPaymentActivity : AppCompatActivity() {

    private lateinit var amountEditText: EditText
    private lateinit var recipientEditText: EditText
    private lateinit var dateTimeEditText: EditText
    private lateinit var transactionIdEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var bankEditText: Spinner
    private lateinit var categoryEditText: AutoCompleteTextView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var backButton: ImageButton

    private lateinit var categoryManager: CategoryManager
    private var categories = listOf<Category>()
    private var selectedCategoryId = "cat_other"
    
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedTime: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_payment)

        // Initialize category manager
        categoryManager = CategoryManager(this)
        categoryManager.initializeDefaultCategories()

        // Initialize views
        initializeViews()

        // Load categories and setup autocomplete
        loadCategories()

        // Populate fields with data from intent
        populateFields()

        // Set up button listeners
        setupButtonListeners()
        
        // Handle back button press
        setupBackPressHandler()
    }

    private fun initializeViews() {
        amountEditText = findViewById(R.id.amountEditText)
        recipientEditText = findViewById(R.id.recipientEditText)
        dateTimeEditText = findViewById(R.id.dateTimeEditText)
        transactionIdEditText = findViewById(R.id.transactionIdEditText)
        noteEditText = findViewById(R.id.noteEditText)
        bankEditText = findViewById(R.id.bankEditText)
        categoryEditText = findViewById(R.id.categoryEditText)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up payment method spinner
        val paymentMethods = arrayOf("Google Pay", "PhonePe", "ICICI Bank", "HDFC Bank", "Paytm", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, paymentMethods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bankEditText.adapter = adapter
        
        // Set back button listener
        backButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        
        // Make dateTimeEditText non-editable and clickable
        dateTimeEditText.isFocusable = false
        dateTimeEditText.isClickable = true
        dateTimeEditText.setOnClickListener {
            showDateTimePicker()
        }
    }

    private fun loadCategories() {
        categories = categoryManager.getAllCategories()
        if (categories.isNotEmpty()) {
            setupCategoryAutocomplete()
        }
    }

    private fun setupCategoryAutocomplete() {
        try {
            val categoryNames = categories.map { "${it.emoji} ${it.name}" }.toMutableList()
            
            val adapter = CategoryArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                categoryNames,
                categories
            )
            
            categoryEditText.setAdapter(adapter)
            categoryEditText.threshold = 1
            
            categoryEditText.setOnItemClickListener { _, view, position, _ ->
                val textView = view as? android.widget.TextView
                val selectedText = textView?.text?.toString() ?: ""
                
                when {
                    selectedText.contains("➕") -> {
                        // Add new category option - extract just the user's input
                        val prefix = "➕ Add new category: "
                        val userInput = if (selectedText.startsWith(prefix)) {
                            selectedText.substringAfter(prefix).trim()
                        } else {
                            selectedText.trim()
                        }
                        if (userInput.isNotEmpty()) {
                            showAddNewCategoryDialog(userInput)
                        }
                    }
                    else -> {
                        // Regular category selected
                        val selectedCategory = categories.firstOrNull { 
                            selectedText.trim() == "${it.emoji} ${it.name}"
                        }
                        if (selectedCategory != null) {
                            selectedCategoryId = selectedCategory.id
                            categoryEditText.setText(selectedText, false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddNewCategoryDialog(categoryName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = dialogView.findViewById<EditText>(R.id.categoryNameInput)
        val emojiInput = dialogView.findViewById<EditText>(R.id.emojiInput)
        
        // Pre-fill the category name from the search
        categoryNameInput.setText(categoryName)
        emojiInput.setText("📁")

        AlertDialog.Builder(this)
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val finalCategoryName = categoryNameInput.text.toString().trim()
                val emoji = emojiInput.text.toString().trim().ifEmpty { "📁" }

                if (finalCategoryName.isNotEmpty()) {
                    val success = categoryManager.addCategory(finalCategoryName, emoji)
                    if (success) {
                        Toast.makeText(this, "Category added!", Toast.LENGTH_SHORT).show()
                        loadCategories() // Reload categories and adapter
                        val newCategory = categories.last()
                        selectedCategoryId = newCategory.id
                        categoryEditText.setText("${newCategory.emoji} ${newCategory.name}")
                    } else {
                        Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter a category name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun populateFields() {
        // Get data from intent
        val amount = intent.getStringExtra("amount") ?: ""
        val recipient = intent.getStringExtra("recipient") ?: ""
        val dateTime = intent.getStringExtra("dateTime") ?: ""
        val transactionId = intent.getStringExtra("transactionId") ?: ""
        val note = intent.getStringExtra("note") ?: ""
        val bankInfo = intent.getStringExtra("bankInfo") ?: ""
        selectedCategoryId = intent.getStringExtra("category") ?: "cat_other"

        // Populate all editable fields
        amountEditText.setText(amount)
        recipientEditText.setText(recipient)
        noteEditText.setText(note)
        transactionIdEditText.setText(transactionId)

        // Set payment method spinner selection
        val paymentMethods = arrayOf("Google Pay", "PhonePe", "ICICI Bank", "HDFC Bank", "Paytm", "Other")
        val paymentIndex = paymentMethods.indexOf(bankInfo)
        if (paymentIndex != -1) {
            bankEditText.setSelection(paymentIndex)
        }

        // Set category field
        val selectedCategory = categories.find { it.id == selectedCategoryId }
        if (selectedCategory != null) {
            categoryEditText.setText("${selectedCategory.emoji} ${selectedCategory.name}")
        }

        // Parse and set date/time
        if (dateTime.isNotEmpty()) {
            dateTimeEditText.setText(dateTime)
            parseDateTimeString(dateTime)
        } else {
            // Set current date/time as default
            updateDateTimeField()
        }
    }

    private fun setupButtonListeners() {
        saveButton.setOnClickListener {
            saveAndReturn()
        }

        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun saveAndReturn() {
        // Get updated values from all editable fields
        val updatedAmount = amountEditText.text.toString().trim()
        val updatedRecipient = recipientEditText.text.toString().trim()
        val updatedDateTime = dateTimeEditText.text.toString().trim()
        val updatedTransactionId = transactionIdEditText.text.toString().trim()
        val updatedNote = noteEditText.text.toString().trim()
        
        // Get selected payment method from spinner
        val updatedBankInfo = bankEditText.selectedItem.toString()

        // Use selected category from grid
        val updatedCategory = selectedCategoryId

        // Validate required fields
        if (updatedAmount.isEmpty()) {
            amountEditText.error = "Amount is required"
            amountEditText.requestFocus()
            return
        }

        if (updatedRecipient.isEmpty()) {
            recipientEditText.error = "Recipient name is required"
            recipientEditText.requestFocus()
            return
        }

        // Create result intent with updated data
        val resultIntent = Intent().apply {
            putExtra("amount", updatedAmount)
            putExtra("recipient", updatedRecipient)
            putExtra("dateTime", updatedDateTime)
            putExtra("transactionId", updatedTransactionId)
            putExtra("note", updatedNote)
            putExtra("bankInfo", updatedBankInfo)
            putExtra("category", updatedCategory)

            // Pass back the editingId if it was provided
            val editingId = intent.getStringExtra("editingId")
            if (editingId != null) {
                putExtra("editingId", editingId)
            }
        }

        // Show success message
        Toast.makeText(this, "Payment details saved!", Toast.LENGTH_SHORT).show()

        // Return the result
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }

    private fun showDateTimePicker() {
        // First show date picker
        showDatePicker()
    }

    private fun showDatePicker() {
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                selectedDate.set(selectedYear, selectedMonth, selectedDay)
                // After date selection, show time picker
                showTimePicker()
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val hour = selectedTime.get(Calendar.HOUR_OF_DAY)
        val minute = selectedTime.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                selectedTime.set(Calendar.HOUR_OF_DAY, selectedHour)
                selectedTime.set(Calendar.MINUTE, selectedMinute)
                updateDateTimeField()
            },
            hour, minute,
            false // 12-hour format
        )
        timePickerDialog.show()
    }

    private fun updateDateTimeField() {
        // Combine date and time
        val calendar = Calendar.getInstance()
        calendar.set(
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH),
            selectedTime.get(Calendar.HOUR_OF_DAY),
            selectedTime.get(Calendar.MINUTE)
        )

        val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        dateTimeEditText.setText(dateTimeFormat.format(calendar.time))
    }

    private fun parseDateTimeString(dateTimeString: String) {
        try {
            val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            val parsedDate = dateTimeFormat.parse(dateTimeString)
            
            if (parsedDate != null) {
                val calendar = Calendar.getInstance()
                calendar.time = parsedDate
                
                selectedDate.set(Calendar.YEAR, calendar.get(Calendar.YEAR))
                selectedDate.set(Calendar.MONTH, calendar.get(Calendar.MONTH))
                selectedDate.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
                
                selectedTime.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                selectedTime.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
            }
        } catch (e: Exception) {
            // If parsing fails, keep default current date/time
        }
    }
}