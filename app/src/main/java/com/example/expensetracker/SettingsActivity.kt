package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageButton
import android.widget.EditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var exportCsvButton: Button
    private lateinit var categoryManager: CategoryManager
    private lateinit var csvManager: CSVManager
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        categoryManager = CategoryManager(this)
        csvManager = CSVManager(this)

        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView)
        exportCsvButton = findViewById(R.id.exportCsvButton)

        categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        loadCategories()

        exportCsvButton.setOnClickListener {
            exportCsv()
        }
    }

    private fun loadCategories() {
        val categories = categoryManager.getAllCategories()
        adapter = CategoryAdapter(categories) { category, action ->
            when (action) {
                "edit" -> showEditCategoryDialog(category)
                "delete" -> confirmDeleteCategory(category)
            }
        }
        categoriesRecyclerView.adapter = adapter
    }

    private fun showEditCategoryDialog(category: Category) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = dialogView.findViewById<EditText>(R.id.categoryNameInput)
        val emojiInput = dialogView.findViewById<EditText>(R.id.emojiInput)

        categoryNameInput.setText(category.name)
        emojiInput.setText(category.emoji)

        AlertDialog.Builder(this)
            .setTitle("Edit Category")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val newName = categoryNameInput.text.toString().trim()
                val newEmoji = emojiInput.text.toString().trim().ifEmpty { category.emoji }

                if (newName.isNotEmpty()) {
                    val success = categoryManager.updateCategory(category.id, newName, newEmoji)
                    if (success) {
                        Toast.makeText(this, "Category updated!", Toast.LENGTH_SHORT).show()
                        loadCategories()
                    } else {
                        Toast.makeText(this, "Error updating category", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteCategory(category: Category) {
        if (category.isPredefined) {
            Toast.makeText(this, "Cannot delete predefined categories", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete \"${category.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val success = categoryManager.deleteCategory(category.id)
                if (success) {
                    Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show()
                    loadCategories()
                } else {
                    Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportCsv() {
        val exportedFile = csvManager.exportToDownloads()

        if (exportedFile != null) {
            AlertDialog.Builder(this)
                .setTitle("Export Successful")
                .setMessage("CSV exported to Downloads folder:\n\n${exportedFile.name}")
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
}

// RecyclerView Adapter for Categories
class CategoryAdapter(
    private val categories: List<Category>,
    private val onAction: (Category, String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryNameText: TextView = view.findViewById(R.id.categoryNameText)
        val categoryTypeText: TextView = view.findViewById(R.id.categoryTypeText)
        val editButton: ImageButton = view.findViewById(R.id.editButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]

        holder.categoryNameText.text = "${category.emoji} ${category.name}"
        holder.categoryTypeText.text = if (category.isPredefined) "Predefined" else "Custom"

        holder.editButton.setOnClickListener {
            onAction(category, "edit")
        }

        holder.deleteButton.setOnClickListener {
            onAction(category, "delete")
        }

        // Disable delete button for predefined categories
        holder.deleteButton.isEnabled = !category.isPredefined
        holder.deleteButton.alpha = if (category.isPredefined) 0.3f else 1.0f
    }

    override fun getItemCount() = categories.size
}