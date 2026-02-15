package com.example.expensetracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var addCategoryFab: ExtendedFloatingActionButton
    private lateinit var addCategoryForm: LinearLayout
    private lateinit var categoryNameInput: TextInputEditText
    private lateinit var addCategoryButton: MaterialButton
    private lateinit var cancelAddCategoryButton: MaterialButton
    private lateinit var darkModeSwitch: SwitchMaterial

    // Emoji options are now input by user from keyboard

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1002
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        csvManager = CSVManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        categoryManager.initializeDefaultCategories()

        setupViews(view)
        setupCategoryRecyclerView()
        setupDarkModeSwitch()

        return view
    }

    private fun setupViews(view: View) {
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView)
        addCategoryFab = view.findViewById(R.id.addCategoryFab)
        addCategoryForm = view.findViewById(R.id.addCategoryForm)
        categoryNameInput = view.findViewById(R.id.categoryNameInput)
        addCategoryButton = view.findViewById(R.id.addCategoryButton)
        cancelAddCategoryButton = view.findViewById(R.id.cancelAddCategoryButton)
        darkModeSwitch = view.findViewById(R.id.darkModeSwitch)

        addCategoryFab.setOnClickListener {
            toggleAddCategoryForm()
        }

        addCategoryButton.setOnClickListener {
            addNewCategory()
        }

        cancelAddCategoryButton.setOnClickListener {
            toggleAddCategoryForm(false)
        }

        val exportButton = view.findViewById<MaterialButton>(R.id.export_csv_button)
        exportButton.setOnClickListener {
            exportCsvToDownloads()
        }

        val clearDataButton = view.findViewById<MaterialButton>(R.id.clear_all_data_button)
        clearDataButton.setOnClickListener {
            confirmClearAllData()
        }
    }

    private fun setupCategoryRecyclerView() {
        categoriesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadCategories()
    }

    private fun loadCategories() {
        val categories = categoryManager.getAllCategories()
        val adapter = CategoryAdapter(categories) { category, action ->
            when (action) {
                "delete" -> confirmDeleteCategory(category)
            }
        }
        categoriesRecyclerView.adapter = adapter
    }

    private fun toggleAddCategoryForm(show: Boolean? = null) {
        val shouldShow = show ?: (addCategoryForm.visibility != View.VISIBLE)
        addCategoryForm.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow) {
            categoryNameInput.text?.clear()
            view?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emojiInput)?.text?.clear()
        }
    }

    private fun addNewCategory() {
        val categoryName = categoryNameInput.text?.toString()?.trim() ?: ""
        if (categoryName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            return
        }

        // Get emoji from text input or use default
        val emojiInput = view?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emojiInput)?.text?.toString()?.trim() ?: ""
        val emoji = if (emojiInput.isNotEmpty()) emojiInput else "📦"

        val success = categoryManager.addCategory(categoryName, emoji)
        if (success) {
            Toast.makeText(requireContext(), "Category added successfully", Toast.LENGTH_SHORT).show()
            loadCategories()
            toggleAddCategoryForm(false)
        } else {
            Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteCategory(category: Category) {
        if (category.isPredefined) {
            Toast.makeText(requireContext(), "Cannot delete predefined categories", Toast.LENGTH_SHORT).show()
            return
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete \"${category.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val success = categoryManager.deleteCategory(category.id)
                if (success) {
                    Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show()
                    loadCategories()
                } else {
                    Toast.makeText(requireContext(), "Error deleting category", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupDarkModeSwitch() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        darkModeSwitch.isChecked = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Note: Dark mode toggle requires app restart or activity recreation
            // For now, we'll just show a message
            Toast.makeText(
                requireContext(),
                "Dark mode toggle requires app restart. This feature will be fully implemented soon.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportCsvToDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE)
                return
            }
        }

        val exportedFile = csvManager.exportToDownloads()

        if (exportedFile != null) {
            Toast.makeText(requireContext(),
                "CSV exported to Downloads!",
                Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAllData() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Clear All Data")
            .setMessage("This will permanently delete all ${csvManager.getTransactionCount()} saved transactions. This cannot be undone!")
            .setPositiveButton("Delete All") { _, _ ->
                val success = csvManager.clearAllTransactions()
                if (success) {
                    Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error clearing data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportCsvToDownloads()
                } else {
                    Toast.makeText(requireContext(),
                        "Storage permission needed for export",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Simple Category Adapter
    private class CategoryAdapter(
        private val categories: List<Category>,
        private val onAction: (Category, String) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

        class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val categoryName: TextView = itemView.findViewById(R.id.categoryNameText)
            val categoryType: TextView = itemView.findViewById(R.id.categoryTypeText)
            val categoryEmoji: TextView = itemView.findViewById(R.id.categoryEmoji)
            val deleteButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return CategoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            val category = categories[position]
            holder.categoryName.text = category.name
            holder.categoryEmoji.text = category.emoji
            holder.categoryType.text = if (category.isPredefined) "Default category" else "Custom"

            holder.deleteButton.setOnClickListener {
                onAction(category, "delete")
            }

            holder.deleteButton.isEnabled = !category.isPredefined
            holder.deleteButton.alpha = if (category.isPredefined) 0.3f else 1.0f
        }

        override fun getItemCount() = categories.size
    }
}