import { useState } from 'react';
import { Transaction } from '../App';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Legend, Tooltip } from 'recharts';
import { TrendingUp, Calendar, Wallet, ChevronDown } from 'lucide-react';
import { useTheme } from '../contexts/ThemeContext';

type ReportsScreenProps = {
  transactions: Transaction[];
};

export default function ReportsScreen({ transactions }: ReportsScreenProps) {
  const { theme } = useTheme();
  const [selectedMonth, setSelectedMonth] = useState<string>('all');

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

  const COLORS = ['#4F46E5', '#6C63FF', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#06B6D4'];

  const totalSpent = filteredTransactions.reduce((sum, t) => sum + t.amount, 0);
  const thisMonth = filteredTransactions
    .filter(t => t.date.getMonth() === new Date().getMonth())
    .reduce((sum, t) => sum + t.amount, 0);
  const topCategory = categoryData.length > 0 
    ? categoryData.reduce((max, cat) => cat.value > max.value ? cat : max)
    : null;
  const recentExpense = filteredTransactions.length > 0 ? filteredTransactions[0] : null;

  return (
    <div className="flex flex-col h-full overflow-auto p-5 pb-20">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-2">
          <h1 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Reports & Analytics</h1>
        </div>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-4`}>Understand your spending patterns</p>
        
        {/* Month Selector */}
        <div className="relative">
          <Calendar className={`absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} pointer-events-none z-10`} />
          <ChevronDown className={`absolute right-4 top-1/2 -translate-y-1/2 w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} pointer-events-none z-10`} />
          <select
            value={selectedMonth}
            onChange={(e) => setSelectedMonth(e.target.value)}
            className={`w-full pl-12 pr-12 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700 text-white' : 'bg-white border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all appearance-none`}
          >
            {monthOptions.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 gap-3 mb-6">
        <div className="bg-gradient-to-br from-[#4F46E5] to-[#6C63FF] rounded-2xl p-4 text-white shadow-lg shadow-indigo-500/20">
          <div className="flex items-center gap-2 mb-2">
            <Wallet className="w-5 h-5" />
            <p className="text-sm text-indigo-100">Total Spent</p>
          </div>
          <p className="text-2xl">₹{totalSpent.toLocaleString('en-IN')}</p>
        </div>
        
        <div className="bg-gradient-to-br from-emerald-500 to-teal-500 rounded-2xl p-4 text-white shadow-lg shadow-emerald-500/20">
          <div className="flex items-center gap-2 mb-2">
            <Calendar className="w-5 h-5" />
            <p className="text-sm text-emerald-100">This Month</p>
          </div>
          <p className="text-2xl">₹{thisMonth.toLocaleString('en-IN')}</p>
        </div>

        {topCategory && (
          <div className="bg-gradient-to-br from-amber-500 to-orange-500 rounded-2xl p-4 text-white shadow-lg shadow-amber-500/20">
            <div className="flex items-center gap-2 mb-2">
              <TrendingUp className="w-5 h-5" />
              <p className="text-sm text-amber-100">Top Category</p>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-2xl">{topCategory.emoji}</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm truncate">{topCategory.name}</p>
                <p className="text-xs text-amber-100">₹{topCategory.value.toLocaleString('en-IN')}</p>
              </div>
            </div>
          </div>
        )}

        {recentExpense && (
          <div className="bg-gradient-to-br from-purple-500 to-pink-500 rounded-2xl p-4 text-white shadow-lg shadow-purple-500/20">
            <p className="text-sm text-purple-100 mb-2">Recent Expense</p>
            <div className="flex items-center gap-2">
              <span className="text-2xl">{recentExpense.emoji}</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm truncate">{recentExpense.recipient}</p>
                <p className="text-xs text-purple-100">₹{recentExpense.amount.toLocaleString('en-IN')}</p>
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
            {categoryData.map((cat, index) => (
              <div key={cat.name} className="flex items-center gap-3">
                <div 
                  className="w-4 h-4 rounded-full flex-shrink-0" 
                  style={{ backgroundColor: COLORS[index % COLORS.length] }}
                />
                <span className="text-xl flex-shrink-0">{cat.emoji}</span>
                <div className="flex-1 min-w-0">
                  <p className={`text-sm ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-700'} truncate`}>{cat.name}</p>
                </div>
                <p className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>₹{cat.value.toLocaleString('en-IN')}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Monthly Trend */}
      {monthlyData.some(m => m.amount > 0) && (
        <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border`}>
          <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Monthly Spending Trend</h2>
          
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={monthlyData}>
                <XAxis 
                  dataKey="month" 
                  tick={{ fontSize: 12, fill: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                  stroke={theme === 'dark' ? '#3f3f46' : '#71717a'}
                />
                <YAxis 
                  tick={{ fontSize: 12, fill: theme === 'dark' ? '#a1a1aa' : '#71717a' }}
                  stroke={theme === 'dark' ? '#3f3f46' : '#71717a'}
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
                />
                <Bar 
                  dataKey="amount" 
                  fill="#4F46E5" 
                  radius={[8, 8, 0, 0]}
                />
              </BarChart>
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