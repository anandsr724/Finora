package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class EditPaymentActivity : AppCompatActivity() {

    private lateinit var amountEditText: EditText
    private lateinit var recipientEditText: EditText
    private lateinit var dateTimeEditText: EditText
    private lateinit var transactionIdEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var bankEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var addCategoryButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private lateinit var categoryManager: CategoryManager
    private var categories = listOf<Category>()
    private var selectedCategoryId = "cat_other"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_payment)

        // Initialize category manager
        categoryManager = CategoryManager(this)
        categoryManager.initializeDefaultCategories()

        // Initialize views
        initializeViews()

        // Load categories and setup spinner
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
        categorySpinner = findViewById(R.id.categorySpinner)
        addCategoryButton = findViewById(R.id.addCategoryButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
    }

    private fun loadCategories() {
        categories = categoryManager.getAllCategories()

        val categoryNames = categories.map { "${it.emoji} ${it.name}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        // Set selection based on current category
        val currentCategoryIndex = categories.indexOfFirst { it.id == selectedCategoryId }
        if (currentCategoryIndex != -1) {
            categorySpinner.setSelection(currentCategoryIndex)
        }
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
        dateTimeEditText.setText(dateTime)
        transactionIdEditText.setText(transactionId)
        noteEditText.setText(note)
        bankEditText.setText(bankInfo)

        // Set category spinner selection
        val categoryIndex = categories.indexOfFirst { it.id == selectedCategoryId }
        if (categoryIndex != -1) {
            categorySpinner.setSelection(categoryIndex)
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

        addCategoryButton.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = dialogView.findViewById<EditText>(R.id.categoryNameInput)
        val emojiInput = dialogView.findViewById<EditText>(R.id.emojiInput)

        AlertDialog.Builder(this)
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val categoryName = categoryNameInput.text.toString().trim()
                val emoji = emojiInput.text.toString().trim().ifEmpty { "📁" }

                if (categoryName.isNotEmpty()) {
                    val success = categoryManager.addCategory(categoryName, emoji)
                    if (success) {
                        Toast.makeText(this, "Category added!", Toast.LENGTH_SHORT).show()
                        loadCategories() // Reload categories
                        // Select the newly added category
                        val newCategoryIndex = categories.size - 1
                        categorySpinner.setSelection(newCategoryIndex)
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

    private fun saveAndReturn() {
        // Get updated values from all editable fields
        val updatedAmount = amountEditText.text.toString().trim()
        val updatedRecipient = recipientEditText.text.toString().trim()
        val updatedDateTime = dateTimeEditText.text.toString().trim()
        val updatedTransactionId = transactionIdEditText.text.toString().trim()
        val updatedNote = noteEditText.text.toString().trim()
        val updatedBankInfo = bankEditText.text.toString().trim()

        // Get selected category
        val selectedPosition = categorySpinner.selectedItemPosition
        val updatedCategory = if (selectedPosition >= 0 && selectedPosition < categories.size) {
            categories[selectedPosition].id
        } else {
            "cat_other"
        }

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
        Toast.makeText(this, "Payment details updated successfully!", Toast.LENGTH_SHORT).show()

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
}