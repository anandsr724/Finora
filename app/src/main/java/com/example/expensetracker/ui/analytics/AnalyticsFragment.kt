package com.example.expensetracker.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.expensetracker.CSVManager
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var monthSpinner: Spinner
    private lateinit var pieChart: PieChart
    private lateinit var lineChart: LineChart
    private lateinit var chartToggleButton: MaterialButton
    private lateinit var totalSpentAmount: TextView
    private lateinit var transactionCountText: TextView
    private lateinit var topCategoryCard: MaterialCardView
    private lateinit var topCategoryName: TextView
    private lateinit var topCategoryIcon: ImageView
    private lateinit var topCategoryAmount: TextView
    private lateinit var recentExpenseCard: MaterialCardView
    private lateinit var recentExpenseRecipient: TextView
    private lateinit var recentExpenseIcon: ImageView
    private lateinit var recentExpenseAmount: TextView
    private lateinit var categoryChartCard: MaterialCardView
    private lateinit var monthlyTrendCard: MaterialCardView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateMessage: TextView
    private lateinit var additionalStatsContainer: LinearLayout
    private lateinit var categoryLegend: LinearLayout

    private var showByCategory = false

    private val chartColors = intArrayOf(
        Color.parseColor("#6B5DD3"),
        Color.parseColor("#8B7DE8"),
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
        lineChart = view.findViewById(R.id.lineChart)
        chartToggleButton = view.findViewById(R.id.chartToggleButton)
        totalSpentAmount = view.findViewById(R.id.totalSpentAmount)
        transactionCountText = view.findViewById(R.id.transactionCountText)
        topCategoryCard = view.findViewById(R.id.topCategoryCard)
        topCategoryName = view.findViewById(R.id.topCategoryName)
        topCategoryIcon = view.findViewById(R.id.topCategoryIcon)
        topCategoryAmount = view.findViewById(R.id.topCategoryAmount)
        recentExpenseCard = view.findViewById(R.id.recentExpenseCard)
        recentExpenseRecipient = view.findViewById(R.id.recentExpenseRecipient)
        recentExpenseIcon = view.findViewById(R.id.recentExpenseIcon)
        recentExpenseAmount = view.findViewById(R.id.recentExpenseAmount)
        categoryChartCard = view.findViewById(R.id.categoryChartCard)
        monthlyTrendCard = view.findViewById(R.id.monthlyTrendCard)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyStateMessage = view.findViewById(R.id.emptyStateMessage)
        additionalStatsContainer = view.findViewById(R.id.additionalStatsContainer)
        categoryLegend = view.findViewById(R.id.categoryLegend)

        monthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadData()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        chartToggleButton.setOnClickListener {
            showByCategory = !showByCategory
            chartToggleButton.text = if (showByCategory) "By Category" else "Total"
            val allTransactions = csvManager.getAllTransactions()
            updateMonthlyTrend(allTransactions)
        }
    }

    private fun setupMonthSpinner() {
        val monthOptions = mutableListOf<String>()
        monthOptions.add("All Time")
        val calendar = Calendar.getInstance()
        for (i in 0 until 12) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)
            monthOptions.add(SimpleDateFormat("MMMM yyyy", Locale("en", "IN")).format(calendar.time))
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, monthOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = adapter
    }

    private fun setupCharts() {
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setEntryLabelColor(Color.TRANSPARENT)
        pieChart.isDrawHoleEnabled = false

        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.setScaleEnabled(false)
        lineChart.setPinchZoom(false)
        lineChart.setExtraOffsets(0f, 10f, 0f, 10f)

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = requireContext().getColor(android.R.color.darker_gray)
        xAxis.textSize = 11f
        xAxis.setDrawAxisLine(false)
        xAxis.granularity = 1f

        lineChart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.parseColor("#22000000")
            textColor = requireContext().getColor(android.R.color.darker_gray)
            textSize = 11f
            setDrawAxisLine(false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "₹${value.toInt()}"
            }
        }
        lineChart.axisRight.isEnabled = false
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
                    val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(transaction.dateTime)
                    if (date != null) {
                        calendar.time = date
                        calendar.get(Calendar.MONTH) == targetMonth && calendar.get(Calendar.YEAR) == targetYear
                    } else false
                } catch (e: Exception) { false }
            }
        }

        if (filteredTransactions.isEmpty()) {
            showEmptyState(selectedMonth == 0)
            return
        }

        hideEmptyState()
        updateSummaryCards(filteredTransactions)
        updateCategoryChart(filteredTransactions)
        updateMonthlyTrend(allTransactions)
    }

    private fun showEmptyState(isAllTime: Boolean) {
        emptyStateLayout.visibility = View.VISIBLE
        categoryChartCard.visibility = View.GONE
        monthlyTrendCard.visibility = View.GONE
        additionalStatsContainer.visibility = View.GONE
        totalSpentAmount.text = "Rs.0"
        transactionCountText.text = "0"
        emptyStateMessage.text = if (isAllTime)
            "Add transactions to see your spending analytics"
        else
            "No transactions found for the selected month"
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
    }

    private fun updateSummaryCards(filteredTransactions: List<PaymentTransaction>) {
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val totalSpent = filteredTransactions.sumOf {
            it.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull() ?: 0.0
        }
        totalSpentAmount.text = "Rs.${fmt.format(totalSpent)}"
        transactionCountText.text = filteredTransactions.size.toString()

        val categoryTotals = filteredTransactions.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull() ?: 0.0 } }

        val topCat = categoryTotals.maxByOrNull { it.value }
        if (topCat != null) {
            val category = categoryManager.getCategoryById(topCat.key)
            topCategoryName.text = category?.name ?: topCat.key
            topCategoryIcon.setImageResource(CategoryIconHelper.getIconResId(topCat.key))
            topCategoryAmount.text = "Rs.${fmt.format(topCat.value)}"
            topCategoryCard.visibility = View.VISIBLE
            additionalStatsContainer.visibility = View.VISIBLE
        } else {
            topCategoryCard.visibility = View.GONE
        }

        val recentExpense = filteredTransactions.maxByOrNull { t ->
            try { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(t.dateTime)?.time ?: 0L }
            catch (e: Exception) { 0L }
        }
        if (recentExpense != null) {
            recentExpenseRecipient.text = recentExpense.recipient
            recentExpenseIcon.setImageResource(CategoryIconHelper.getIconResId(recentExpense.category))
            val num = recentExpense.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull()
            recentExpenseAmount.text = if (num != null) "Rs.${fmt.format(num)}" else recentExpense.amount
            recentExpenseCard.visibility = View.VISIBLE
            additionalStatsContainer.visibility = View.VISIBLE
        } else {
            recentExpenseCard.visibility = View.GONE
        }
    }

    private fun updateCategoryChart(transactions: List<PaymentTransaction>) {
        val categoryTotals = transactions.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull() ?: 0.0 } }
            .toList().sortedByDescending { it.second }

        if (categoryTotals.isEmpty()) { categoryChartCard.visibility = View.GONE; return }
        categoryChartCard.visibility = View.VISIBLE

        val pieEntries = categoryTotals.map { (catId, amount) ->
            PieEntry(amount.toFloat(), categoryManager.getCategoryById(catId)?.name ?: catId)
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
        pieChart.data = PieData(dataSet)
        pieChart.invalidate()
        updateCategoryLegend(categoryTotals)
    }

    private fun updateCategoryLegend(categoryTotals: List<Pair<String, Double>>) {
        categoryLegend.removeAllViews()
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val density = resources.displayMetrics.density

        categoryTotals.forEachIndexed { idx, (catId, amount) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            }

            val dot = android.view.View(requireContext()).apply {
                val size = (10 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (12 * density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(chartColors[idx % chartColors.size])
                }
            }

            val nameView = android.widget.TextView(requireContext()).apply {
                text = categoryManager.getCategoryById(catId)?.name ?: catId
                textSize = 13f
                setTextColor(Color.parseColor("#2D3142"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amountView = android.widget.TextView(requireContext()).apply {
                text = "₹${fmt.format(amount)}"
                textSize = 13f
                setTextColor(Color.parseColor("#71717A"))
            }

            row.addView(dot)
            row.addView(nameView)
            row.addView(amountView)
            categoryLegend.addView(row)

            if (idx < categoryTotals.size - 1) {
                val divider = android.view.View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#E4E4E7"))
                }
                categoryLegend.addView(divider)
            }
        }
    }

    private fun updateMonthlyTrend(allTransactions: List<PaymentTransaction>) {
        val monthLabels = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        val monthRanges = (5 downTo 0).map { i ->
            val cal = Calendar.getInstance().apply { time = Date(); add(Calendar.MONTH, -i) }
            monthLabels.add(SimpleDateFormat("MMM", Locale("en", "IN")).format(cal.time))
            Triple(i, cal.get(Calendar.MONTH), cal.get(Calendar.YEAR))
        }

        val xFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx >= 0 && idx < monthLabels.size) monthLabels[idx] else ""
            }
        }

        if (showByCategory) {
            val categoryIds = allTransactions.map { it.category }.distinct()
            val dataSets = categoryIds.mapIndexed { idx, catId ->
                val entries = monthRanges.mapIndexed { mIdx, (_, month, year) ->
                    val total = allTransactions.filter { t ->
                        t.category == catId && try {
                            val d = dateFormat.parse(t.dateTime)
                            d != null && calendar.apply { time = d }.get(Calendar.MONTH) == month
                                    && calendar.get(Calendar.YEAR) == year
                        } catch (e: Exception) { false }
                    }.sumOf { it.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull() ?: 0.0 }
                    Entry(mIdx.toFloat(), total.toFloat())
                }
                LineDataSet(entries, categoryManager.getCategoryById(catId)?.name ?: catId).apply {
                    val c = chartColors[idx % chartColors.size]
                    color = c; lineWidth = 2f; setCircleColor(c); circleRadius = 3f
                    setDrawCircleHole(false); setDrawValues(false); mode = LineDataSet.Mode.CUBIC_BEZIER
                }
            }
            if (dataSets.all { ds -> ds.values.all { it.y == 0f } }) { monthlyTrendCard.visibility = View.GONE; return }
            monthlyTrendCard.visibility = View.VISIBLE
            lineChart.legend.isEnabled = true
            lineChart.legend.form = Legend.LegendForm.LINE
            lineChart.legend.textSize = 11f
            lineChart.xAxis.valueFormatter = xFormatter
            lineChart.data = LineData(dataSets)
        } else {
            val entries = monthRanges.mapIndexed { mIdx, (_, month, year) ->
                val total = allTransactions.filter { t ->
                    try {
                        val d = dateFormat.parse(t.dateTime)
                        d != null && calendar.apply { time = d }.get(Calendar.MONTH) == month
                                && calendar.get(Calendar.YEAR) == year
                    } catch (e: Exception) { false }
                }.sumOf { it.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull() ?: 0.0 }
                Entry(mIdx.toFloat(), total.toFloat())
            }
            if (entries.all { it.y == 0f }) { monthlyTrendCard.visibility = View.GONE; return }
            val ds = LineDataSet(entries, "Total").apply {
                color = chartColors[0]; lineWidth = 3f; setCircleColor(chartColors[0])
                circleRadius = 5f; circleHoleRadius = 2.5f; setDrawCircleHole(true)
                circleHoleColor = Color.WHITE; setDrawValues(false); mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true); fillColor = chartColors[0]; fillAlpha = 40
            }
            monthlyTrendCard.visibility = View.VISIBLE
            lineChart.legend.isEnabled = false
            lineChart.xAxis.valueFormatter = xFormatter
            lineChart.data = LineData(ds)
        }
        lineChart.invalidate()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
