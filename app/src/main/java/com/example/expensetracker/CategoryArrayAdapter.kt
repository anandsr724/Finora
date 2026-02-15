package com.example.expensetracker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

class CategoryArrayAdapter(
    context: Context,
    resource: Int,
    private val originalList: MutableList<String>,
    private val categories: List<Category>,
    private val onCategorySelected: (String) -> Unit = { },
    private val onAddNewCategory: (String) -> Unit = { }
) : ArrayAdapter<String>(context, resource, originalList), Filterable {

    private var filteredList = mutableListOf<String>()
    private var showAddNewOption = false
    private var searchQuery = ""

    init {
        filteredList.addAll(originalList)
    }

    override fun getCount(): Int {
        return if (showAddNewOption) filteredList.size + 1 else filteredList.size
    }

    override fun getItem(position: Int): String? {
        return if (position < filteredList.size) {
            filteredList[position]
        } else if (showAddNewOption) {
            "➕ Add new category: $searchQuery"
        } else {
            null
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.textSize = 14f
        textView.setPadding(8, 6, 8, 6)
        
        if (position < filteredList.size) {
            textView.text = filteredList[position]
        } else if (showAddNewOption) {
            textView.text = "➕ Add new category: $searchQuery"
        }
        
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.textSize = 14f
        textView.setPadding(8, 6, 8, 6)
        
        if (position < filteredList.size) {
            textView.text = filteredList[position]
        } else if (showAddNewOption) {
            textView.text = "➕ Add new category: $searchQuery"
        }
        
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                searchQuery = constraint?.toString()?.trim() ?: ""
                
                filteredList.clear()
                showAddNewOption = false
                
                if (searchQuery.isEmpty()) {
                    // Show all categories when nothing is typed
                    filteredList.addAll(originalList)
                } else {
                    // Filter by search query
                    for (item in originalList) {
                        if (item.contains(searchQuery, ignoreCase = true)) {
                            filteredList.add(item)
                        }
                    }
                    
                    // Show "Add new" if non-empty search and no exact match
                    val hasExactMatch = filteredList.any { 
                        it.trim().equals(searchQuery, ignoreCase = true)
                    }
                    showAddNewOption = !hasExactMatch
                }
                
                results.values = filteredList
                results.count = if (showAddNewOption) filteredList.size + 1 else filteredList.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }
        }
    }
}
