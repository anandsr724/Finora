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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.expensetracker.CSVManager
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.CurrencyManager
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
    private lateinit var avgMomContainer: LinearLayout
    private lateinit var avgTransactionAmount: TextView
    private lateinit var momChangeText: TextView

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var showByCategory = false
    private val monthYearPairs = mutableListOf<Pair<Int, Int>>() // (year, month) for spinner positions 1+

    private val chartColors = intArrayOf(
        Color.parseColor("#6366F1"),  // indigo
        Color.parseColor("#10B981"),  // emerald
        Color.parseColor("#F59E0B"),  // amber
        Color.parseColor("#EF4444"),  // red
        Color.parseColor("#06B6D4"),  // cyan
        Color.parseColor("#EC4899"),  // pink
        Color.parseColor("#84CC16"),  // lime
        Color.parseColor("#F97316")   // orange
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
        setupCharts()
        loadData()

        return view
    }

    private fun setupViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_indigo)
        swipeRefreshLayout.setOnRefreshListener {
            loadData()
            swipeRefreshLayout.isRefreshing = false
        }
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
        avgMomContainer = view.findViewById(R.id.avgMomContainer)
        avgTransactionAmount = view.findViewById(R.id.avgTransactionAmount)
        momChangeText = view.findViewById(R.id.momChangeText)

        // Listener is installed inside rebuildMonthSpinner; nothing to set here

        chartToggleButton.setOnClickListener {
            showByCategory = !showByCategory
            chartToggleButton.text = if (showByCategory) "By Category" else "Total"
            val expenseTransactions = csvManager.getAllTransactions().filter { it.type == "expense" }
            updateMonthlyTrend(expenseTransactions)
        }
    }

    private fun rebuildMonthSpinner(transactions: List<com.example.expensetracker.PaymentTransaction>): Int {
        val options = mutableListOf("All Time")
        monthYearPairs.clear()

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        val distinctMonths = transactions.mapNotNull { tx ->
            try {
                val d = dateFormat.parse(tx.dateTime) ?: return@mapNotNull null
                cal.time = d
                Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            } catch (e: Exception) { null }
        }.distinct().sortedByDescending { (y, m) -> y * 12 + m }

        val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale("en", "IN"))
        distinctMonths.forEach { (year, month) ->
            cal.set(year, month, 1)
            options.add(monthLabelFormat.format(cal.time))
            monthYearPairs.add(Pair(year, month))
        }

        // Preserve current selection by year/month value
        val currentYearMonth = if (monthSpinner.selectedItemPosition > 0 &&
            monthSpinner.selectedItemPosition <= monthYearPairs.size) {
            monthYearPairs[monthSpinner.selectedItemPosition - 1]
        } else null

        monthSpinner.onItemSelectedListener = null
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_month, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_month)
        monthSpinner.adapter = adapter

        val newPos = if (currentYearMonth != null) {
            val idx = monthYearPairs.indexOf(currentYearMonth)
            if (idx >= 0) idx + 1 else 0
        } else 0
        monthSpinner.setSelection(newPos)

        monthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderData(position, csvManager.getAllTransactions())
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        return newPos
    }

    private fun setupCharts() {
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setEntryLabelColor(Color.TRANSPARENT)
        pieChart.isDrawHoleEnabled = false
        pieChart.setExtraOffsets(24f, 16f, 24f, 16f)  // room for outside percentage labels

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
                override fun getFormattedValue(value: Float): String {
                    val sym = CurrencyManager.getSymbol(CurrencyManager.getDefault(requireContext()))
                    return "$sym${value.toInt()}"
                }
            }
        }
        lineChart.axisRight.isEnabled = false
    }

    private fun loadData() {
        val allTransactions = csvManager.getAllTransactions()
        val position = rebuildMonthSpinner(allTransactions)
        renderData(position, allTransactions)
    }

    private fun renderData(selectedMonth: Int, allTransactions: List<com.example.expensetracker.PaymentTransaction>) {
        // Analytics is spending-focused — only consider expense transactions throughout
        val expenseTransactions = allTransactions.filter { it.type == "expense" }
        val filteredTransactions = if (selectedMonth == 0 || selectedMonth > monthYearPairs.size) {
            expenseTransactions
        } else {
            val (targetYear, targetMonth) = monthYearPairs[selectedMonth - 1]
            val cal = Calendar.getInstance()
            expenseTransactions.filter { transaction ->
                try {
                    val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(transaction.dateTime)
                    if (date != null) {
                        cal.time = date
                        cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
                    } else false
                } catch (e: Exception) { false }
            }
        }

        if (filteredTransactions.isEmpty()) {
            showEmptyState(selectedMonth == 0)
            return
        }

        hideEmptyState()
        updateSummaryCards(filteredTransactions, selectedMonth, expenseTransactions)
        updateCategoryChart(filteredTransactions)
        updateMonthlyTrend(expenseTransactions)
    }

    private fun showEmptyState(isAllTime: Boolean) {
        emptyStateLayout.visibility = View.VISIBLE
        categoryChartCard.visibility = View.GONE
        monthlyTrendCard.visibility = View.GONE
        additionalStatsContainer.visibility = View.GONE
        avgMomContainer.visibility = View.GONE
        totalSpentAmount.text = "${CurrencyManager.getSymbol(CurrencyManager.getDefault(requireContext()))}0"
        transactionCountText.text = "0"
        emptyStateMessage.text = if (isAllTime)
            "Add transactions to see your spending analytics"
        else
            "No transactions found for the selected month"
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
    }

    private fun updateSummaryCards(
        filteredTransactions: List<PaymentTransaction>,
        selectedMonthIdx: Int = 0,
        allTransactions: List<PaymentTransaction> = emptyList()
    ) {
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val defaultCurrency = CurrencyManager.getDefault(requireContext())
        val sym = CurrencyManager.getSymbol(defaultCurrency)

        val totalSpent = filteredTransactions.sumOf {
            CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency)
        }
        totalSpentAmount.text = "$sym${fmt.format(totalSpent)}"
        transactionCountText.text = filteredTransactions.size.toString()

        // Average per transaction
        val count = filteredTransactions.size
        val avg = if (count > 0) totalSpent / count else 0.0
        avgTransactionAmount.text = "$sym${fmt.format(avg)}"

        // Month-over-month change
        if (selectedMonthIdx > 0 && selectedMonthIdx <= monthYearPairs.size && allTransactions.isNotEmpty()) {
            val (curYear, curMonth) = monthYearPairs[selectedMonthIdx - 1]
            val cal = Calendar.getInstance()
            cal.set(curYear, curMonth, 1)
            cal.add(Calendar.MONTH, -1)
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = cal.get(Calendar.MONTH)
            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            val prevTotal = allTransactions.filter { tx ->
                if (tx.type != "expense") return@filter false
                try {
                    val d = dateFormat.parse(tx.dateTime) ?: return@filter false
                    cal.time = d
                    cal.get(Calendar.MONTH) == prevMonth && cal.get(Calendar.YEAR) == prevYear
                } catch (e: Exception) { false }
            }.sumOf { CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency) }

            if (prevTotal > 0.0) {
                val pct = ((totalSpent - prevTotal) / prevTotal) * 100.0
                val sign = if (pct >= 0) "+" else ""
                momChangeText.text = "$sign${fmt.format(pct.toInt())}%"
                momChangeText.setTextColor(resources.getColor(
                    if (pct <= 0) android.R.color.holo_green_dark else android.R.color.holo_red_dark, null
                ))
            } else {
                momChangeText.text = "New"
                momChangeText.setTextColor(resources.getColor(R.color.primary_indigo, null))
            }
            avgMomContainer.visibility = View.VISIBLE
        } else if (selectedMonthIdx == 0 && count > 0) {
            momChangeText.text = "—"
            momChangeText.setTextColor(resources.getColor(R.color.text_secondary_light, null))
            avgMomContainer.visibility = View.VISIBLE
        } else {
            avgMomContainer.visibility = View.GONE
        }

        val categoryTotals = filteredTransactions.groupBy { it.category }
            .mapValues { (_, txns) ->
                txns.sumOf { CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency) }
            }

        val topCat = categoryTotals.maxByOrNull { it.value }
        if (topCat != null) {
            val category = categoryManager.getCategoryById(topCat.key)
            topCategoryName.text = category?.name ?: topCat.key
            topCategoryIcon.setImageResource(CategoryIconHelper.getIconResId(topCat.key))
            topCategoryAmount.text = "$sym${fmt.format(topCat.value)}"
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
            val converted = CurrencyManager.convert(CurrencyManager.parseAmount(recentExpense.amount), recentExpense.currency, defaultCurrency)
            recentExpenseAmount.text = "$sym${fmt.format(converted)}"
            recentExpenseCard.visibility = View.VISIBLE
            additionalStatsContainer.visibility = View.VISIBLE
        } else {
            recentExpenseCard.visibility = View.GONE
        }
    }

    private fun updateCategoryChart(transactions: List<PaymentTransaction>) {
        val defaultCurrency = CurrencyManager.getDefault(requireContext())
        val categoryTotals = transactions.groupBy { it.category }
            .mapValues { (_, txns) ->
                txns.sumOf { CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency) }
            }
            .toList().sortedByDescending { it.second }

        if (categoryTotals.isEmpty()) { categoryChartCard.visibility = View.GONE; return }
        categoryChartCard.visibility = View.VISIBLE

        val grandTotal = categoryTotals.sumOf { it.second }
        val threshold = grandTotal * 0.05  // club categories below 5% into "Others"

        val mainCategories = categoryTotals.filter { it.second >= threshold }
        val smallCategories = categoryTotals.filter { it.second < threshold }
        val othersTotal = smallCategories.sumOf { it.second }

        val pieEntries = mutableListOf<PieEntry>()
        mainCategories.forEach { (catId, amount) ->
            pieEntries.add(PieEntry(amount.toFloat(), categoryManager.getCategoryById(catId)?.name ?: catId))
        }
        if (smallCategories.isNotEmpty()) {
            pieEntries.add(PieEntry(othersTotal.toFloat(), "Others"))
        }

        val total = pieEntries.sumOf { it.value.toDouble() }.toFloat()
        val colors = chartColors.take(mainCategories.size).toMutableList()
        if (smallCategories.isNotEmpty()) colors.add(Color.parseColor("#A1A1AA"))  // neutral gray for Others

        val dataSet = PieDataSet(pieEntries, "")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.valueTextColor = Color.parseColor("#2D3142")
        dataSet.valueTextSize = 11f
        dataSet.valueLineColor = Color.parseColor("#A1A1AA")
        dataSet.valueLineWidth = 1f
        dataSet.valueLinePart1Length = 0.7f
        dataSet.valueLinePart2Length = 0.45f
        dataSet.valueLinePart1OffsetPercentage = 80f
        val othersValue = othersTotal.toFloat()
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val pct = value / total * 100
                return if (pct < 4f && value != othersValue) "" else "${pct.toInt()}%"
            }
        }
        pieChart.data = PieData(dataSet)
        pieChart.invalidate()
        updateCategoryLegend(mainCategories, smallCategories)
    }

    private fun updateCategoryLegend(
        mainCategories: List<Pair<String, Double>>,
        smallCategories: List<Pair<String, Double>>
    ) {
        categoryLegend.removeAllViews()
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val sym = CurrencyManager.getSymbol(CurrencyManager.getDefault(requireContext()))
        val density = resources.displayMetrics.density

        val allRows = mainCategories.mapIndexed { idx, (catId, amount) ->
            Triple(categoryManager.getCategoryById(catId)?.name ?: catId, amount, chartColors[idx % chartColors.size])
        }.toMutableList()

        if (smallCategories.isNotEmpty()) {
            val othersTotal = smallCategories.sumOf { it.second }
            val othersLabel = if (smallCategories.size == 1)
                categoryManager.getCategoryById(smallCategories[0].first)?.name ?: smallCategories[0].first
            else "Others (${smallCategories.size})"
            allRows.add(Triple(othersLabel, othersTotal, Color.parseColor("#A1A1AA")))
        }

        allRows.forEachIndexed { idx, (name, amount, color) ->
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
                    setColor(color)
                }
            }

            val nameView = android.widget.TextView(requireContext()).apply {
                text = name
                textSize = 13f
                setTextColor(Color.parseColor("#2D3142"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amountView = android.widget.TextView(requireContext()).apply {
                text = "$sym${fmt.format(amount)}"
                textSize = 13f
                setTextColor(Color.parseColor("#71717A"))
            }

            row.addView(dot)
            row.addView(nameView)
            row.addView(amountView)
            categoryLegend.addView(row)

            if (idx < allRows.size - 1) {
                val divider = android.view.View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#E4E4E7"))
                }
                categoryLegend.addView(divider)
            }
        }
    }

    private fun updateMonthlyTrend(allTransactions: List<PaymentTransaction>) {
        val defaultCurrency = CurrencyManager.getDefault(requireContext())
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
                    }.sumOf { CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency) }
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
                }.sumOf { CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency) }
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
