package com.example.expensetracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
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
    private lateinit var saveScreenshotsSwitch: SwitchMaterial
    private lateinit var feedbackSwitch: SwitchMaterial

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1002
        private const val IMPORT_CSV_REQUEST = 1005
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
        saveScreenshotsSwitch = view.findViewById(R.id.saveScreenshotsSwitch)
        feedbackSwitch = view.findViewById(R.id.feedbackSwitch)

        manageCategoriesButton.setOnClickListener {
            showManageCategoriesSheet()
        }

        view.findViewById<MaterialButton>(R.id.export_csv_button).setOnClickListener {
            exportCsvToDownloads()
        }

        view.findViewById<MaterialButton>(R.id.import_csv_button).setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "text/csv"
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(android.content.Intent.createChooser(intent, "Select CSV file"), IMPORT_CSV_REQUEST)
        }

        view.findViewById<MaterialButton>(R.id.clear_all_data_button).setOnClickListener {
            confirmClearAllData()
        }

        view.findViewById<LinearLayout>(R.id.uploadStatementRow).setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.example.expensetracker.StatementImportActivity::class.java))
        }

        setupCurrencySetting(view)
        setupDevTrackingSwitch()
        setupFeedbackSwitch()
    }

    private fun setupCurrencySetting(view: View) {
        val currencyRow = view.findViewById<LinearLayout>(R.id.defaultCurrencyRow)
        val currencyValue = view.findViewById<TextView>(R.id.defaultCurrencyValue)

        fun refreshDisplay() {
            val code = com.example.expensetracker.CurrencyManager.getDefault(requireContext())
            val info = com.example.expensetracker.CurrencyManager.getInfo(code)
            currencyValue.text = "${info?.symbol ?: code}  $code"
        }
        refreshDisplay()

        currencyRow.setOnClickListener {
            val currencies = com.example.expensetracker.CurrencyManager.CURRENCIES
            val items = currencies.map { "${it.symbol}  ${it.code} — ${it.name}" }.toTypedArray()
            val currentCode = com.example.expensetracker.CurrencyManager.getDefault(requireContext())
            val currentIdx = currencies.indexOfFirst { it.code == currentCode }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Default Currency")
                .setSingleChoiceItems(items, currentIdx) { dialog, idx ->
                    com.example.expensetracker.CurrencyManager.setDefault(
                        requireContext(), currencies[idx].code
                    )
                    refreshDisplay()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadCategoryPreview() {
        val categories = categoryManager.getAllCategories()
        categoriesCountText.text = "${categories.size} categories"
        categoryPreviewContainer.removeAllViews()
        categories.forEach { category ->
            val chip = layoutInflater.inflate(R.layout.item_category_chip, categoryPreviewContainer, false)
            chip.findViewById<ImageView>(R.id.chipIcon)
                .setImageResource(CategoryIconHelper.getIconResId(category))
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
            recycler.adapter = ManageCategoryAdapter(
                cats,
                onDelete = { category ->
                    confirmDeleteCategory(category) {
                        loadCategoryPreview()
                        refreshSheet()
                    }
                },
                onEdit = { category ->
                    sheet.dismiss()
                    showEditCategoryDialog(category) {
                        loadCategoryPreview()
                        showManageCategoriesSheet()
                    }
                }
            )
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

        sheetView.findViewById<MaterialButton>(R.id.restoreDefaultsButton).setOnClickListener {
            val restored = categoryManager.restoreDefaultCategories()
            val msg = if (restored > 0) "Restored $restored default categories" else "All defaults already present"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            loadCategoryPreview()
            refreshSheet()
        }

        sheet.show()
    }

    private fun showAddNewCategoryDialog(onAdded: () -> Unit) {
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = sheetView.findViewById<EditText>(R.id.categoryNameInput)
        val iconPickerGrid = sheetView.findViewById<GridLayout>(R.id.iconPickerGrid)

        val sheet = BottomSheetDialog(requireContext())
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
            val frame = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0; height = cellSize
                    setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                }
                background = makeBackground(false)
            }
            val img = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageResource(resId)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
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
                    Toast.makeText(requireContext(), "Category added!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    onAdded()
                } else {
                    Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show()
    }

    private fun showEditCategoryDialog(category: Category, onUpdated: () -> Unit) {
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = sheetView.findViewById<EditText>(R.id.categoryNameInput)
        val iconPickerGrid = sheetView.findViewById<GridLayout>(R.id.iconPickerGrid)

        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.sheetTitle).text = "Edit Category"
        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).text = "Save Changes"
        categoryNameInput.setText(category.name)

        sheetView.findViewById<ImageButton>(R.id.closeButton).setOnClickListener { sheet.dismiss() }
        sheetView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { sheet.dismiss() }

        val allKeys = CategoryIconHelper.allIconKeys
        val iconOptions = allKeys.map { key -> key to CategoryIconHelper.getIconResId(key) }
        var selectedIconKey = when {
            category.emoji in allKeys -> category.emoji
            category.id in allKeys -> category.id
            else -> "cat_other"
        }
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
            val frame = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0; height = cellSize
                    setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                }
                background = makeBackground(false)
            }
            val img = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageResource(resId)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                setColorFilter(Color.parseColor("#6B5DD3"))
            }
            frame.addView(img)
            frame.setOnClickListener { selectedIconKey = key; updateSelection(idx) }
            iconViews.add(frame to img)
            iconPickerGrid.addView(frame)
        }
        val initialIdx = allKeys.indexOf(selectedIconKey).coerceAtLeast(0)
        updateSelection(initialIdx)

        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).setOnClickListener {
            val name = categoryNameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val success = categoryManager.updateCategory(category.id, name, selectedIconKey)
                if (success) {
                    Toast.makeText(requireContext(), "Category updated!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    onUpdated()
                } else {
                    Toast.makeText(requireContext(), "Error updating category", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show()
    }

    private fun confirmDeleteCategory(category: Category, onDeleted: () -> Unit) {
        val affectedCount = csvManager.getAllTransactions().count { it.category == category.id }
        val warningLine = if (affectedCount > 0)
            "\n\n$affectedCount transaction${if (affectedCount == 1) "" else "s"} using this category will show as Uncategorized."
        else ""
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete \"${category.name}\"?$warningLine")
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

    private fun setupDevTrackingSwitch() {
        val prefs = requireContext().getSharedPreferences("finora_prefs", Context.MODE_PRIVATE)
        saveScreenshotsSwitch.isChecked = prefs.getBoolean("dev_tracking_enabled", false)
        saveScreenshotsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dev_tracking_enabled", isChecked).apply()
            val msg = if (isChecked) "OCR data capture enabled"
                      else "OCR data capture disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFeedbackSwitch() {
        val prefs = requireContext().getSharedPreferences("finora_prefs", Context.MODE_PRIVATE)
        feedbackSwitch.isChecked = prefs.getBoolean("feedback_enabled", false)
        feedbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("feedback_enabled", isChecked).apply()
            val msg = if (isChecked) "Feedback prompt enabled" else "Feedback prompt disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDarkModeSwitch() {
        val prefs = requireContext().getSharedPreferences("finora_prefs", Context.MODE_PRIVATE)
        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode", false)

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
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

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_CSV_REQUEST && resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            val (imported, skipped) = csvManager.importFromCsv(uri)
            val msg = if (imported > 0)
                "Imported $imported transaction${if (imported == 1) "" else "s"}${if (skipped > 0) " ($skipped skipped)" else ""}"
            else
                "No new transactions found"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private inner class ManageCategoryAdapter(
        private val categories: List<Category>,
        private val onDelete: (Category) -> Unit,
        private val onEdit: (Category) -> Unit
    ) : RecyclerView.Adapter<ManageCategoryAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val emoji: ImageView = itemView.findViewById(R.id.categoryEmoji)
            val name: TextView = itemView.findViewById(R.id.categoryNameText)
            val type: TextView = itemView.findViewById(R.id.categoryTypeText)
            val editButton: ImageButton = itemView.findViewById(R.id.editButton)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.emoji.setImageResource(CategoryIconHelper.getIconResId(category))
            holder.name.text = category.name
            holder.type.text = if (category.isPredefined) "Default category" else "Custom"
            holder.editButton.setOnClickListener { onEdit(category) }
            holder.deleteButton.setOnClickListener { onDelete(category) }
        }

        override fun getItemCount() = categories.size
    }
}
