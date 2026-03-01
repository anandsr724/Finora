import { useState } from 'react';
import { Transaction } from '../App';
import { PieChart, Pie, Cell, LineChart, Line, XAxis, YAxis, ResponsiveContainer, Legend, Tooltip, Area, AreaChart, BarChart, Bar } from 'recharts';
import { TrendingUp, Calendar, Wallet, ChevronDown, Check } from 'lucide-react';
import { useTheme } from '../contexts/ThemeContext';
import { getIcon } from '../utils/iconMapping';

type ReportsScreenProps = {
  transactions: Transaction[];
};

export default function ReportsScreen({ transactions }: ReportsScreenProps) {
  const { theme } = useTheme();
  const [selectedMonth, setSelectedMonth] = useState<string>('all');
  const [showCategoryLines, setShowCategoryLines] = useState<boolean>(false);
  const [showMonthDropdown, setShowMonthDropdown] = useState<boolean>(false);

  // Generate month options (last 12 months + current month)
  const monthOptions: { value: string; label: string; month: number; year: number }[] = [
    { value: 'all', label: 'All Time', month: -1, year: -1 }
  ];
  
  for (let i = 0; i < 12; i++) {
    const date = new Date();
    date.setMonth(date.getMonth() - i);
    monthOptions.push({
      value: `${date.getFullYear()}-${date.getMonth()}`,
      label: date.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' }),
      month: date.getMonth(),
      year: date.getFullYear(),
    });
  }

  // Filter transactions by selected month
  const filteredTransactions = selectedMonth === 'all' 
    ? transactions 
    : transactions.filter(t => {
        const selectedOption = monthOptions.find(opt => opt.value === selectedMonth);
        if (!selectedOption) return true;
        return t.date.getMonth() === selectedOption.month && 
               t.date.getFullYear() === selectedOption.year;
      });

  // Calculate summary statistics from filtered transactions
  const totalSpent = filteredTransactions.reduce((sum, t) => sum + t.amount, 0);
  const avgTransaction = filteredTransactions.length > 0 
    ? totalSpent / filteredTransactions.length 
    : 0;
  const transactionCount = filteredTransactions.length;

  // Calculate spending by category
  const categoryData = filteredTransactions.reduce((acc, t) => {
    const existing = acc.find(item => item.name === t.category);
    if (existing) {
      existing.value += t.amount;
    } else {
      acc.push({
        name: t.category,
        value: t.amount,
        emoji: t.emoji,
      });
    }
    return acc;
  }, [] as { name: string; value: number; emoji: string }[]);

  // Calculate monthly spending (last 6 months)
  const monthlyData: { month: string; amount: number }[] = [];
  for (let i = 5; i >= 0; i--) {
    const date = new Date();
    date.setMonth(date.getMonth() - i);
    const monthName = date.toLocaleDateString('en-IN', { month: 'short' });
    const monthTransactions = transactions.filter(t => 
      t.date.getMonth() === date.getMonth() && 
      t.date.getFullYear() === date.getFullYear()
    );
    const total = monthTransactions.reduce((sum, t) => sum + t.amount, 0);
    monthlyData.push({ month: monthName, amount: total });
  }

  // Calculate monthly spending by category (last 6 months)
  const monthlyCategoryData: Record<string, any>[] = [];
  const allCategories = new Set<string>();
  
  for (let i = 5; i >= 0; i--) {
    const date = new Date();
    date.setMonth(date.getMonth() - i);
    const monthName = date.toLocaleDateString('en-IN', { month: 'short' });
    const monthTransactions = transactions.filter(t => 
      t.date.getMonth() === date.getMonth() && 
      t.date.getFullYear() === date.getFullYear()
    );
    
    const categoryTotals: Record<string, number> = { month: monthName };
    monthTransactions.forEach(t => {
      allCategories.add(t.category);
      categoryTotals[t.category] = (categoryTotals[t.category] || 0) + t.amount;
    });
    
    monthlyCategoryData.push(categoryTotals);
  }

  const COLORS = ['#6B5DD3', '#8B7DE8', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#06B6D4'];

  const thisMonth = filteredTransactions
    .filter(t => t.date.getMonth() === new Date().getMonth())
    .reduce((sum, t) => sum + t.amount, 0);
  const topCategory = categoryData.length > 0 
    ? categoryData.reduce((max, cat) => cat.value > max.value ? cat : max)
    : null;
  const recentExpense = filteredTransactions.length > 0 ? filteredTransactions[0] : null;

  return (
    <div className={`flex flex-col h-full overflow-auto px-6 py-6 pb-20 ${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F5F5F7]'}`}>
      {/* Header */}
      <div className="mb-6">
        <h1 className={`text-2xl font-bold mb-2 ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>Reports</h1>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-4`}>Analyze your spending patterns</p>
        
        {/* Month Selector */}
        <div className="relative">
          <button
            onClick={() => setShowMonthDropdown(!showMonthDropdown)}
            className={`w-full flex items-center gap-3 px-4 py-3.5 rounded-2xl ${
              theme === 'dark' ? 'bg-zinc-800 text-white' : 'bg-white shadow-sm'
            } transition-all`}
          >
            <Calendar className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`} />
            <span className="flex-1 text-left">
              {monthOptions.find(opt => opt.value === selectedMonth)?.label || 'All Time'}
            </span>
            <ChevronDown className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} transition-transform ${
              showMonthDropdown ? 'rotate-180' : ''
            }`} />
          </button>

          {/* Dropdown Menu */}
          {showMonthDropdown && (
            <>
              <div 
                className="fixed inset-0 z-10" 
                onClick={() => setShowMonthDropdown(false)}
              />
              <div className={`absolute top-full left-0 right-0 mt-2 ${
                theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-200'
              } rounded-2xl shadow-lg border z-20 max-h-64 overflow-y-auto`}>
                {monthOptions.map(option => (
                  <button
                    key={option.value}
                    onClick={() => {
                      setSelectedMonth(option.value);
                      setShowMonthDropdown(false);
                    }}
                    className={`w-full flex items-center justify-between px-4 py-3 transition-colors ${
                      selectedMonth === option.value
                        ? theme === 'dark'
                          ? 'bg-[#6B5DD3]/20 text-[#6B5DD3]'
                          : 'bg-[#6B5DD3]/10 text-[#6B5DD3]'
                        : theme === 'dark'
                          ? 'hover:bg-zinc-700 text-zinc-300'
                          : 'hover:bg-zinc-50 text-zinc-700'
                    } first:rounded-t-2xl last:rounded-b-2xl`}
                  >
                    <span className="text-sm">{option.label}</span>
                    {selectedMonth === option.value && (
                      <Check className="w-4 h-4" />
                    )}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 gap-3 mb-6">
        <div className="bg-gradient-to-br from-[#6B5DD3] to-[#8B7DE8] rounded-2xl p-4 text-white shadow-lg shadow-purple-500/20">
          <div className="flex items-center gap-2 mb-2">
            <Wallet className="w-5 h-5" />
            <p className="text-sm text-purple-100">Total Spent</p>
          </div>
          <p className="text-2xl font-semibold">₹{totalSpent.toLocaleString('en-IN')}</p>
        </div>
        
        <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-2xl p-4 shadow-sm border`}>
          <div className="flex items-center gap-2 mb-2">
            <TrendingUp className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`} />
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>Transactions</p>
          </div>
          <p className={`text-2xl font-semibold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>{transactionCount}</p>
        </div>

        {topCategory && (
          <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-2xl p-4 shadow-sm border`}>
            <div className="flex items-center gap-2 mb-2">
              <TrendingUp className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`} />
              <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>Top Category</p>
            </div>
            <div className="flex items-center gap-2">
              {(() => {
                const IconComponent = getIcon(topCategory.emoji);
                return <IconComponent className="w-6 h-6 text-[#6B5DD3]" />;
              })()}
              <div className="flex-1 min-w-0">
                <p className={`text-sm font-medium truncate ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>{topCategory.name}</p>
                <p className={`text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>₹{topCategory.value.toLocaleString('en-IN')}</p>
              </div>
            </div>
          </div>
        )}

        {recentExpense && (
          <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-2xl p-4 shadow-sm border`}>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-2`}>Recent Expense</p>
            <div className="flex items-center gap-2">
              {(() => {
                const IconComponent = getIcon(recentExpense.emoji);
                return <IconComponent className="w-6 h-6 text-[#6B5DD3]" />;
              })()}
              <div className="flex-1 min-w-0">
                <p className={`text-sm font-medium truncate ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>{recentExpense.recipient}</p>
                <p className={`text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>₹{recentExpense.amount.toLocaleString('en-IN')}</p>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Spending by Category */}
      {categoryData.length > 0 && (
        <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border mb-6`}>
          <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Spending by Category</h2>
          
          <div className="h-64 mb-4">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={categoryData}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  outerRadius={80}
                  fill="#8884d8"
                  dataKey="value"
                  label={({ name, percent }) => `${(percent * 100).toFixed(0)}%`}
                >
                  {categoryData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip 
                  formatter={(value: number) => `₹${value.toLocaleString('en-IN')}`}
                  contentStyle={{
                    backgroundColor: theme === 'dark' ? '#27272a' : 'white',
                    border: `1px solid ${theme === 'dark' ? '#3f3f46' : '#e4e4e7'}`,
                    borderRadius: '12px',
                    color: theme === 'dark' ? '#fafafa' : '#18181b'
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>

          <div className="space-y-2">
            {categoryData.map((cat, index) => {
              const IconComponent = getIcon(cat.emoji);
              return (
                <div key={cat.name} className="flex items-center gap-3">
                  <div 
                    className="w-4 h-4 rounded-full flex-shrink-0" 
                    style={{ backgroundColor: COLORS[index % COLORS.length] }}
                  />
                  <IconComponent className="w-5 h-5 text-[#6B5DD3] flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className={`text-sm ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-700'} truncate`}>{cat.name}</p>
                  </div>
                  <p className={`text-sm font-medium ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>₹{cat.value.toLocaleString('en-IN')}</p>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Monthly Trend */}
      {monthlyData.some(m => m.amount > 0) && (
        <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border`}>
          <div className="flex items-center justify-between mb-4">
            <h2 className={`font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>Monthly Spending Trend</h2>
            
            {/* Toggle Button */}
            <button
              onClick={() => setShowCategoryLines(!showCategoryLines)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                showCategoryLines
                  ? 'bg-[#6B5DD3] text-white shadow-sm'
                  : theme === 'dark' 
                    ? 'bg-zinc-700 text-zinc-300 hover:bg-zinc-600' 
                    : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
              }`}
            >
              {showCategoryLines ? 'By Category' : 'Total'}
            </button>
          </div>
          
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              {!showCategoryLines ? (
                <AreaChart data={monthlyData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorAmount" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6B5DD3" stopOpacity={0.3}/>
                      <stop offset="95%" stopColor="#6B5DD3" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <XAxis 
                    dataKey="month" 
                    tick={{ fontSize: 12, fill: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis 
                    tick={{ fontSize: 12, fill: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                    axisLine={false}
                    tickLine={false}
                    tickFormatter={(value) => `₹${value}`}
                  />
                  <Tooltip 
                    formatter={(value: number) => [`₹${value.toLocaleString('en-IN')}`, 'Spent']}
                    contentStyle={{
                      backgroundColor: theme === 'dark' ? '#27272a' : 'white',
                      border: `1px solid ${theme === 'dark' ? '#3f3f46' : '#e4e4e7'}`,
                      borderRadius: '12px',
                      fontSize: '14px',
                      color: theme === 'dark' ? '#fafafa' : '#18181b'
                    }}
                    labelStyle={{ color: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                  />
                  <Area 
                    type="monotone" 
                    dataKey="amount" 
                    stroke="#6B5DD3" 
                    strokeWidth={3}
                    fill="url(#colorAmount)"
                    dot={{ fill: '#6B5DD3', strokeWidth: 2, r: 4, stroke: '#fff' }}
                    activeDot={{ r: 6, stroke: '#fff', strokeWidth: 2 }}
                  />
                </AreaChart>
              ) : (
                <LineChart data={monthlyCategoryData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                  <defs>
                    {Array.from(allCategories).map((category, index) => (
                      <linearGradient key={category} id={`gradient-${category}`} x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={COLORS[index % COLORS.length]} stopOpacity={0.1}/>
                        <stop offset="95%" stopColor={COLORS[index % COLORS.length]} stopOpacity={0}/>
                      </linearGradient>
                    ))}
                  </defs>
                  <XAxis 
                    dataKey="month" 
                    tick={{ fontSize: 12, fill: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis 
                    tick={{ fontSize: 12, fill: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                    axisLine={false}
                    tickLine={false}
                    tickFormatter={(value) => `₹${value}`}
                  />
                  <Tooltip 
                    formatter={(value: number) => `₹${value.toLocaleString('en-IN')}`}
                    contentStyle={{
                      backgroundColor: theme === 'dark' ? '#27272a' : 'white',
                      border: `1px solid ${theme === 'dark' ? '#3f3f46' : '#e4e4e7'}`,
                      borderRadius: '12px',
                      fontSize: '14px',
                      color: theme === 'dark' ? '#fafafa' : '#18181b'
                    }}
                    labelStyle={{ color: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                  />
                  <Legend 
                    wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }}
                    iconType="line"
                  />
                  {Array.from(allCategories).map((category, index) => (
                    <Line
                      key={category}
                      type="monotone"
                      dataKey={category}
                      stroke={COLORS[index % COLORS.length]}
                      strokeWidth={2.5}
                      dot={{ fill: COLORS[index % COLORS.length], strokeWidth: 2, r: 3, stroke: '#fff' }}
                      activeDot={{ r: 5, stroke: '#fff', strokeWidth: 2 }}
                      connectNulls
                    />
                  ))}
                </LineChart>
              )}
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {filteredTransactions.length === 0 && (
        <div className="flex flex-col items-center justify-center py-12">
          <div className={`w-24 h-24 ${theme === 'dark' ? 'bg-zinc-800' : 'bg-zinc-100'} rounded-full flex items-center justify-center mb-4`}>
            <TrendingUp className={`w-12 h-12 ${theme === 'dark' ? 'text-zinc-600' : 'text-zinc-400'}`} />
          </div>
          <h3 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>No data yet</h3>
          <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} text-center px-8`}>
            {selectedMonth === 'all' 
              ? 'Add transactions to see your spending analytics'
              : 'No transactions found for the selected month'}
          </p>
        </div>
      )}
    </div>
  );
}