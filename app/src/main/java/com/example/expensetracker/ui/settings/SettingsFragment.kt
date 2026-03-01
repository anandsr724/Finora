package com.example.expensetracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var manageCategoriesButton: MaterialButton
    private lateinit var categoriesCountText: TextView
    private lateinit var categoryPreviewContainer: LinearLayout
    private lateinit var darkModeSwitch: SwitchMaterial

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
        loadCategoryPreview()
        setupDarkModeSwitch()

        return view
    }

    private fun setupViews(view: View) {
        manageCategoriesButton = view.findViewById(R.id.manageCategoriesButton)
        categoriesCountText = view.findViewById(R.id.categoriesCountText)
        categoryPreviewContainer = view.findViewById(R.id.categoryPreviewContainer)
        darkModeSwitch = view.findViewById(R.id.darkModeSwitch)

        manageCategoriesButton.setOnClickListener {
            showManageCategoriesSheet()
        }

        view.findViewById<MaterialButton>(R.id.export_csv_button).setOnClickListener {
            exportCsvToDownloads()
        }

        view.findViewById<MaterialButton>(R.id.clear_all_data_button).setOnClickListener {
            confirmClearAllData()
        }
    }

    private fun loadCategoryPreview() {
        val categories = categoryManager.getAllCategories()
        categoriesCountText.text = "${categories.size} categories"
        categoryPreviewContainer.removeAllViews()
        categories.forEach { category ->
            val chip = layoutInflater.inflate(R.layout.item_category_chip, categoryPreviewContainer, false)
            chip.findViewById<ImageView>(R.id.chipIcon)
                .setImageResource(CategoryIconHelper.getIconResId(category.id))
            chip.findViewById<android.widget.TextView>(R.id.chipName).text = category.name
            categoryPreviewContainer.addView(chip)
        }
    }

    private fun showManageCategoriesSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_manage_categories_sheet, null)
        sheet.setContentView(sheetView)

        val recycler = sheetView.findViewById<RecyclerView>(R.id.manageCategoriesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        fun refreshSheet() {
            val cats = categoryManager.getAllCategories()
            categoriesCountText.text = "${cats.size} categories"
            recycler.adapter = ManageCategoryAdapter(cats) { category ->
                confirmDeleteCategory(category) {
                    loadCategoryPreview()
                    refreshSheet()
                }
            }
        }
        refreshSheet()

        sheetView.findViewById<ImageButton>(R.id.closeCategoriesSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.addNewCategoryButton).setOnClickListener {
            sheet.dismiss()
            showAddNewCategoryDialog {
                loadCategoryPreview()
            }
        }

        sheet.show()
    }

    private fun showAddNewCategoryDialog(onAdded: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = dialogView.findViewById<EditText>(R.id.categoryNameInput)
        val emojiInput = dialogView.findViewById<EditText>(R.id.emojiInput)
        emojiInput.setText("📁")

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = categoryNameInput.text.toString().trim()
                val emoji = emojiInput.text.toString().trim().ifEmpty { "📁" }
                if (name.isNotEmpty()) {
                    val success = categoryManager.addCategory(name, emoji)
                    if (success) {
                        Toast.makeText(requireContext(), "Category added!", Toast.LENGTH_SHORT).show()
                        onAdded()
                    } else {
                        Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteCategory(category: Category, onDeleted: () -> Unit) {
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
                    onDeleted()
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

        darkModeSwitch.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(
                requireContext(),
                "Dark mode toggle requires app restart. This feature will be fully implemented soon.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportCsvToDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
                return
            }
        }

        val exportedFile = csvManager.exportToDownloads()
        if (exportedFile != null) {
            Toast.makeText(requireContext(), "CSV exported to Downloads!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAllData() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Clear All Data")
            .setMessage(
                "This will permanently delete all ${csvManager.getTransactionCount()} saved transactions. This cannot be undone!"
            )
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportCsvToDownloads()
            } else {
                Toast.makeText(
                    requireContext(), "Storage permission needed for export", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private inner class ManageCategoryAdapter(
        private val categories: List<Category>,
        private val onDelete: (Category) -> Unit
    ) : RecyclerView.Adapter<ManageCategoryAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val emoji: ImageView = itemView.findViewById(R.id.categoryEmoji)
            val name: TextView = itemView.findViewById(R.id.categoryNameText)
            val type: TextView = itemView.findViewById(R.id.categoryTypeText)
            val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.emoji.setImageResource(CategoryIconHelper.getIconResId(category.id))
            holder.name.text = category.name
            holder.type.text = if (category.isPredefined) "Default category" else "Custom"
            holder.deleteButton.isEnabled = !category.isPredefined
            holder.deleteButton.alpha = if (category.isPredefined) 0.3f else 1.0f
            holder.deleteButton.setOnClickListener { onDelete(category) }
        }

        override fun getItemCount() = categories.size
    }
}
