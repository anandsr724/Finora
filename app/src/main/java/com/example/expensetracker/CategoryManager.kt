package com.example.expensetracker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Category(
    val id: String,
    val name: String,
    val emoji: String = "📁",
    val isPredefined: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("emoji", emoji)
            put("isPredefined", isPredefined)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Category {
            return Category(
                id = json.getString("id"),
                name = json.getString("name"),
                emoji = json.optString("emoji", "📁"),
                isPredefined = json.optBoolean("isPredefined", false)
            )
        }
    }
}

object CategoryIconHelper {
    fun getIconResId(categoryId: String): Int {
        return when (categoryId) {
            "cat_food" -> R.drawable.ic_cat_food
            "cat_groceries" -> R.drawable.ic_cat_groceries
            "cat_transport" -> R.drawable.ic_cat_transport
            "cat_rent" -> R.drawable.ic_cat_rent
            "cat_utilities" -> R.drawable.ic_cat_utilities
            "cat_health" -> R.drawable.ic_cat_health
            "cat_entertainment" -> R.drawable.ic_cat_entertainment
            "cat_shopping" -> R.drawable.ic_cat_shopping
            "cat_education" -> R.drawable.ic_cat_education
            "cat_travel" -> R.drawable.ic_cat_travel
            "cat_personal" -> R.drawable.ic_cat_personal
            "cat_coffee" -> R.drawable.ic_cat_coffee
            "cat_dumbbell" -> R.drawable.ic_cat_dumbbell
            "cat_pets" -> R.drawable.ic_cat_pets
            "cat_work" -> R.drawable.ic_cat_work
            "cat_gifts" -> R.drawable.ic_cat_gifts
            "cat_music" -> R.drawable.ic_cat_music
            "cat_gaming" -> R.drawable.ic_cat_gaming
            "cat_pizza" -> R.drawable.ic_cat_pizza
            "cat_bus" -> R.drawable.ic_cat_bus
            "cat_fuel" -> R.drawable.ic_cat_fuel
            "cat_lightbulb" -> R.drawable.ic_cat_lightbulb
            "cat_wifi" -> R.drawable.ic_cat_wifi
            "cat_phone" -> R.drawable.ic_cat_phone
            "cat_tv" -> R.drawable.ic_cat_tv
            "cat_hospital" -> R.drawable.ic_cat_hospital
            "cat_pill" -> R.drawable.ic_cat_pill
            "cat_stethoscope" -> R.drawable.ic_cat_stethoscope
            "cat_book" -> R.drawable.ic_cat_book
            "cat_school" -> R.drawable.ic_cat_school
            else -> R.drawable.ic_cat_other
        }
    }

    // Check emoji field first (handles custom cats and edited predefined cats with a stored icon key)
    fun getIconResId(category: Category): Int {
        if (category.emoji in allIconKeys) return getIconResId(category.emoji)
        if (category.isPredefined) return getIconResId(category.id)
        return R.drawable.ic_cat_other
    }

    // All selectable icon keys for the icon picker UI
    val allIconKeys = listOf(
        "cat_food", "cat_groceries", "cat_transport", "cat_rent", "cat_utilities",
        "cat_health", "cat_entertainment", "cat_shopping", "cat_education", "cat_travel",
        "cat_personal", "cat_coffee", "cat_dumbbell", "cat_pets", "cat_work",
        "cat_gifts", "cat_music", "cat_gaming", "cat_pizza", "cat_bus",
        "cat_fuel", "cat_lightbulb", "cat_wifi", "cat_phone", "cat_tv",
        "cat_hospital", "cat_pill", "cat_stethoscope", "cat_book", "cat_school",
        "cat_other"
    )

    // Luminous Finance chart palette, used everywhere a category needs a semantic tint
    // (transaction row icon badges, category badges, analytics legend/pie, statement review rows).
    private val tintPalette = intArrayOf(
        R.color.chart_color_1, R.color.chart_color_2, R.color.chart_color_3, R.color.chart_color_4,
        R.color.chart_color_5, R.color.chart_color_6, R.color.chart_color_7, R.color.chart_color_8
    )

    // Deterministic hash-based assignment so every category id (predefined or custom) gets a
    // stable color without needing a hand-maintained per-id map.
    fun getIconTintColorRes(categoryId: String): Int {
        val index = Math.floorMod(categoryId.hashCode(), tintPalette.size)
        return tintPalette[index]
    }
}

class CategoryManager(private val context: Context) {

    companion object {
        private const val CATEGORIES_FILENAME = "categories.json"
        private const val TAG = "CategoryManager"

        // Predefined categories with emojis
        val DEFAULT_CATEGORIES = listOf(
            Category("cat_food", "Food & Dining", "🍔", true),
            Category("cat_groceries", "Groceries", "🛒", true),
            Category("cat_transport", "Transportation", "🚗", true),
            Category("cat_rent", "Rent", "🏠", true),
            Category("cat_utilities", "Utilities", "💡", true),
            Category("cat_health", "Health & Medical", "⚕️", true),
            Category("cat_entertainment", "Entertainment", "🎬", true),
            Category("cat_shopping", "Shopping", "🛍️", true),
            Category("cat_education", "Education", "📚", true),
            Category("cat_travel", "Travel", "✈️", true),
            Category("cat_personal", "Personal Care", "💇", true),
            Category("cat_other", "Other", "📁", true)
        )
    }

    private val categoriesFile: File
        get() = File(context.filesDir, CATEGORIES_FILENAME)

    // Initialize with default categories if file doesn't exist
    fun initializeDefaultCategories() {
        if (!categoriesFile.exists()) {
            saveCategories(DEFAULT_CATEGORIES)
            Log.d(TAG, "Initialized default categories")
        }
    }

    // Get all categories
    fun getAllCategories(): List<Category> {
        if (!categoriesFile.exists()) {
            initializeDefaultCategories()
            return DEFAULT_CATEGORIES
        }

        return try {
            val jsonString = categoriesFile.readText()
            val jsonArray = JSONArray(jsonString)
            val categories = mutableListOf<Category>()

            for (i in 0 until jsonArray.length()) {
                val categoryJson = jsonArray.getJSONObject(i)
                categories.add(Category.fromJson(categoryJson))
            }

            categories
        } catch (e: Exception) {
            Log.e(TAG, "Error reading categories", e)
            DEFAULT_CATEGORIES
        }
    }

    // Save categories to file
    private fun saveCategories(categories: List<Category>) {
        try {
            val jsonArray = JSONArray()
            categories.forEach { category ->
                jsonArray.put(category.toJson())
            }

            categoriesFile.writeText(jsonArray.toString())
            Log.d(TAG, "Categories saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving categories", e)
        }
    }

    // Add a new category
    fun addCategory(name: String, emoji: String = "📁"): Boolean {
        if (name.isBlank()) return false

        return try {
            val categories = getAllCategories().toMutableList()

            // Check for duplicate names
            if (categories.any { it.name.equals(name, ignoreCase = true) }) {
                Log.w(TAG, "Category already exists: $name")
                return false
            }

            val newCategory = Category(
                id = "cat_custom_${System.currentTimeMillis()}",
                name = name,
                emoji = emoji,
                isPredefined = false
            )

            categories.add(newCategory)
            saveCategories(categories)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding category", e)
            false
        }
    }

    // Update an existing category
    fun updateCategory(categoryId: String, newName: String, newEmoji: String): Boolean {
        return try {
            val categories = getAllCategories().toMutableList()
            val index = categories.indexOfFirst { it.id == categoryId }

            if (index != -1) {
                val category = categories[index]
                categories[index] = category.copy(name = newName, emoji = newEmoji)
                saveCategories(categories)
                Log.d(TAG, "Category updated: $categoryId")
                true
            } else {
                Log.w(TAG, "Category not found: $categoryId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating category", e)
            false
        }
    }

    // Delete a category
    fun deleteCategory(categoryId: String): Boolean {
        return try {
            val categories = getAllCategories().toMutableList()
            val category = categories.find { it.id == categoryId }

            if (category != null) {
                categories.removeIf { it.id == categoryId }
                saveCategories(categories)
                Log.d(TAG, "Category deleted: $categoryId")
                true
            } else {
                Log.w(TAG, "Category not found: $categoryId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category", e)
            false
        }
    }

    // Restore any missing default categories (returns count restored)
    fun restoreDefaultCategories(): Int {
        val current = getAllCategories().toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val missing = DEFAULT_CATEGORIES.filter { it.id !in existingIds }
        if (missing.isEmpty()) return 0
        current.addAll(0, missing)
        saveCategories(current)
        return missing.size
    }

    // Get category by ID
    fun getCategoryById(categoryId: String): Category? {
        return getAllCategories().find { it.id == categoryId }
    }

    // Get category display name (just the name)
    fun getCategoryDisplayName(categoryId: String): String {
        val category = getCategoryById(categoryId)
        return category?.name ?: "Uncategorized"
    }

    // Get category emoji
    fun getCategoryEmoji(categoryId: String): String {
        val category = getCategoryById(categoryId)
        return category?.emoji ?: "📁"
    }
}