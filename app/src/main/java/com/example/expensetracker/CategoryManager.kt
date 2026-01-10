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

    // Delete a category (only custom categories can be deleted)
    fun deleteCategory(categoryId: String): Boolean {
        return try {
            val categories = getAllCategories().toMutableList()
            val category = categories.find { it.id == categoryId }

            if (category != null && !category.isPredefined) {
                categories.removeIf { it.id == categoryId }
                saveCategories(categories)
                Log.d(TAG, "Category deleted: $categoryId")
                true
            } else {
                Log.w(TAG, "Cannot delete predefined category or category not found: $categoryId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category", e)
            false
        }
    }

    // Get category by ID
    fun getCategoryById(categoryId: String): Category? {
        return getAllCategories().find { it.id == categoryId }
    }

    // Get category name with emoji
    fun getCategoryDisplayName(categoryId: String): String {
        val category = getCategoryById(categoryId)
        return if (category != null) {
            "${category.emoji} ${category.name}"
        } else {
            "Uncategorized"
        }
    }
}