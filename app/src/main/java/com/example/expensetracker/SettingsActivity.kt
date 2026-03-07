package com.example.expensetracker

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = sheetView.findViewById<EditText>(R.id.categoryNameInput)
        val iconPickerGrid = sheetView.findViewById<GridLayout>(R.id.iconPickerGrid)

        categoryNameInput.setText(category.name)

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.sheetTitle).text = "Edit Category"
        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).text = "Update"

        sheetView.findViewById<ImageButton>(R.id.closeButton).setOnClickListener { sheet.dismiss() }
        sheetView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { sheet.dismiss() }

        val iconOptions = CategoryIconHelper.allIconKeys.map { key -> key to CategoryIconHelper.getIconResId(key) }
        var selectedIconKey = if (category.isPredefined) category.id else category.emoji.ifEmpty { "cat_other" }
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
        val initialIdx = iconOptions.indexOfFirst { it.first == selectedIconKey }.coerceAtLeast(0)
        updateSelection(initialIdx)

        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).setOnClickListener {
            val newName = categoryNameInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                val success = categoryManager.updateCategory(category.id, newName, selectedIconKey)
                if (success) {
                    Toast.makeText(this, "Category updated!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    loadCategories()
                } else {
                    Toast.makeText(this, "Error updating category", Toast.LENGTH_SHORT).show()
                }
            }
        }

        sheet.show()
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