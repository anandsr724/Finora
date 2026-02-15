package com.example.expensetracker.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.expensetracker.CSVManager
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var monthSpinner: Spinner
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var totalSpentAmount: TextView
    private lateinit var thisMonthAmount: TextView
    private lateinit var topCategoryCard: MaterialCardView
    private lateinit var topCategoryName: TextView
    private lateinit var topCategoryEmoji: TextView
    private lateinit var topCategoryAmount: TextView
    private lateinit var recentExpenseCard: MaterialCardView
    private lateinit var recentExpenseRecipient: TextView
    private lateinit var recentExpenseEmoji: TextView
    private lateinit var recentExpenseAmount: TextView
    private lateinit var categoryChartCard: MaterialCardView
    private lateinit var monthlyTrendCard: MaterialCardView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateMessage: TextView
    private lateinit var additionalStatsContainer: LinearLayout

    private val chartColors = intArrayOf(
        Color.parseColor("#4F46E5"),
        Color.parseColor("#6C63FF"),
        Color.parseColor("#10B981"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#EF4444"),
        Color.parseColor("#8B5CF6"),
        Color.parseColor("#EC4899"),
        Color.parseColor("#06B6D4")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_analytics, container, false)
        csvManager = CSVManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        categoryManager.initializeDefaultCategories()

        setupViews(view)
        setupMonthSpinner()
        setupCharts()
        loadData()

        return view
    }

    private fun setupViews(view: View) {
        monthSpinner = view.findViewById(R.id.monthSpinner)
        pieChart = view.findViewById(R.id.pieChart)
        barChart = view.findViewById(R.id.barChart)
        totalSpentAmount = view.findViewById(R.id.totalSpentAmount)
        thisMonthAmount = view.findViewById(R.id.thisMonthAmount)
        topCategoryCard = view.findViewById(R.id.topCategoryCard)
        topCategoryName = view.findViewById(R.id.topCategoryName)
        topCategoryEmoji = view.findViewById(R.id.topCategoryEmoji)
        topCategoryAmount = view.findViewById(R.id.topCategoryAmount)
        recentExpenseCard = view.findViewById(R.id.recentExpenseCard)
        recentExpenseRecipient = view.findViewById(R.id.recentExpenseRecipient)
        recentExpenseEmoji = view.findViewById(R.id.recentExpenseEmoji)
        recentExpenseAmount = view.findViewById(R.id.recentExpenseAmount)
        categoryChartCard = view.findViewById(R.id.categoryChartCard)
        monthlyTrendCard = view.findViewById(R.id.monthlyTrendCard)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyStateMessage = view.findViewById(R.id.emptyStateMessage)
        additionalStatsContainer = view.findViewById(R.id.additionalStatsContainer)

        monthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadData()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupMonthSpinner() {
        val monthOptions = mutableListOf<Pair<String, String>>()
        monthOptions.add(Pair("all", "All Time"))

        val calendar = Calendar.getInstance()
        for (i in 0 until 12) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)
            val monthName = SimpleDateFormat("MMMM yyyy", Locale("en", "IN")).format(calendar.time)
            val monthValue = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"
            monthOptions.add(Pair(monthValue, monthName))
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            monthOptions.map { it.second }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = adapter
    }

    private fun setupCharts() {
        // Pie Chart Setup
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setEntryLabelColor(Color.TRANSPARENT)
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.setTransparentCircleColor(Color.TRANSPARENT)

        // Bar Chart Setup
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setScaleEnabled(false)
        barChart.setPinchZoom(false)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = requireContext().getColor(android.R.color.darker_gray)
        xAxis.textSize = 12f

        val yAxisLeft = barChart.axisLeft
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.textColor = requireContext().getColor(android.R.color.darker_gray)
        yAxisLeft.textSize = 12f
        yAxisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₹${value.toInt()}"
            }
        }

        barChart.axisRight.isEnabled = false
    }

    private fun loadData() {
        val allTransactions = csvManager.getAllTransactions()
        val selectedMonth = monthSpinner.selectedItemPosition

        val filteredTransactions = if (selectedMonth == 0) {
            allTransactions
        } else {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -(selectedMonth - 1))
            val targetMonth = calendar.get(Calendar.MONTH)
            val targetYear = calendar.get(Calendar.YEAR)

            allTransactions.filter { transaction ->
                try {
                    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                    val date = dateFormat.parse(transaction.dateTime)
                    if (date != null) {
                        calendar.time = date
                        calendar.get(Calendar.MONTH) == targetMonth &&
                                calendar.get(Calendar.YEAR) == targetYear
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            showEmptyState(selectedMonth == 0)
            return
        }

        hideEmptyState()
        updateSummaryCards(filteredTransactions, allTransactions)
        updateCategoryChart(filteredTransactions)
        updateMonthlyTrend(allTransactions)
    }

    private fun showEmptyState(isAllTime: Boolean) {
        emptyStateLayout.visibility = View.VISIBLE
        categoryChartCard.visibility = View.GONE
        monthlyTrendCard.visibility = View.GONE
        additionalStatsContainer.visibility = View.GONE
        emptyStateMessage.text = if (isAllTime) {
            "Add transactions to see your spending analytics"
        } else {
            "No transactions found for the selected month"
        }
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
    }

    private fun updateSummaryCards(filteredTransactions: List<PaymentTransaction>, allTransactions: List<PaymentTransaction>) {
        val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))

        // Total Spent
        val totalSpent = filteredTransactions.sumOf { 
            it.amount.replace("₹", "").replace(",", "").toDoubleOrNull() ?: 0.0 
        }
        totalSpentAmount.text = "₹${numberFormat.format(totalSpent)}"

        // This Month
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        val thisMonthTotal = filteredTransactions.filter { transaction ->
            try {
                val date = dateFormat.parse(transaction.dateTime)
                if (date != null) {
                    calendar.time = date
                    calendar.get(Calendar.MONTH) == currentMonth &&
                            calendar.get(Calendar.YEAR) == currentYear
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }.sumOf { it.amount.replace("₹", "").replace(",", "").toDoubleOrNull() ?: 0.0 }

        thisMonthAmount.text = "₹${numberFormat.format(thisMonthTotal)}"

        // Top Category
        val categoryTotals = filteredTransactions.groupBy { it.category }
            .mapValues { (_, transactions) ->
                transactions.sumOf { 
                    it.amount.replace("₹", "").replace(",", "").toDoubleOrNull() ?: 0.0 
                }
            }

        val topCategory = categoryTotals.maxByOrNull { it.value }
        if (topCategory != null) {
            val category = categoryManager.getCategoryById(topCategory.key)
            topCategoryName.text = category?.name ?: topCategory.key
            topCategoryEmoji.text = category?.emoji ?: "📁"
            topCategoryAmount.text = "₹${numberFormat.format(topCategory.value)}"
            topCategoryCard.visibility = View.VISIBLE
            additionalStatsContainer.visibility = View.VISIBLE
        } else {
            topCategoryCard.visibility = View.GONE
        }

        // Recent Expense
        val recentExpense = filteredTransactions.maxByOrNull { transaction ->
            try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                dateFormat.parse(transaction.dateTime)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        if (recentExpense != null) {
            val category = categoryManager.getCategoryById(recentExpense.category)
            recentExpenseRecipient.text = recentExpense.recipient
            recentExpenseEmoji.text = category?.emoji ?: "📁"
            recentExpenseAmount.text = recentExpense.amount
            recentExpenseCard.visibility = View.VISIBLE
            additionalStatsContainer.visibility = View.VISIBLE
        } else {
            recentExpenseCard.visibility = View.GONE
        }
    }

    private fun updateCategoryChart(transactions: List<PaymentTransaction>) {
        val categoryTotals = transactions.groupBy { it.category }
            .mapValues { (_, transactions) ->
                transactions.sumOf { 
                    it.amount.replace("₹", "").replace(",", "").toDoubleOrNull() ?: 0.0 
                }
            }
            .toList()
            .sortedByDescending { it.second }

        if (categoryTotals.isEmpty()) {
            categoryChartCard.visibility = View.GONE
            return
        }

        categoryChartCard.visibility = View.VISIBLE

        val pieEntries = categoryTotals.mapIndexed { index, (categoryId, amount) ->
            val category = categoryManager.getCategoryById(categoryId)
            PieEntry(amount.toFloat(), category?.name ?: categoryId)
        }

        val dataSet = PieDataSet(pieEntries, "")
        dataSet.colors = chartColors.toList()
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val total = pieEntries.sumOf { it.value.toDouble() }.toFloat()
                return "${(value / total * 100).toInt()}%"
            }
        }

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.invalidate()
    }

    private fun updateMonthlyTrend(allTransactions: List<PaymentTransaction>) {
        val monthlyData = mutableListOf<Pair<String, Double>>()
        val calendar = Calendar.getInstance()

        for (i in 5 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)
            val monthName = SimpleDateFormat("MMM", Locale("en", "IN")).format(calendar.time)
            val targetMonth = calendar.get(Calendar.MONTH)
            val targetYear = calendar.get(Calendar.YEAR)

            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            val monthTotal = allTransactions.filter { transaction ->
                try {
                    val date = dateFormat.parse(transaction.dateTime)
                    if (date != null) {
                        calendar.time = date
                        calendar.get(Calendar.MONTH) == targetMonth &&
                                calendar.get(Calendar.YEAR) == targetYear
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }.sumOf { 
                it.amount.replace("₹", "").replace(",", "").toDoubleOrNull() ?: 0.0 
            }

            monthlyData.add(Pair(monthName, monthTotal))
        }

        if (monthlyData.all { it.second == 0.0 }) {
            monthlyTrendCard.visibility = View.GONE
            return
        }

        monthlyTrendCard.visibility = View.VISIBLE

        val barEntries = monthlyData.mapIndexed { index, (_, amount) ->
            BarEntry(index.toFloat(), amount.toFloat())
        }

        val dataSet = BarDataSet(barEntries, "")
        dataSet.color = chartColors[0]
        dataSet.valueTextColor = requireContext().getColor(android.R.color.darker_gray)
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) "₹${value.toInt()}" else ""
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f
        barChart.data = barData

        val xAxisLabels = monthlyData.map { it.first }
        barChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < xAxisLabels.size) {
                    xAxisLabels[index]
                } else {
                    ""
                }
            }
        }

        barChart.invalidate()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}