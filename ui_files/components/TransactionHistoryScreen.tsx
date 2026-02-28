import { useState } from 'react';
import { Transaction } from '../App';
import { Search, Filter, Edit2, Trash2, FileText } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { useTheme } from '../contexts/ThemeContext';

type TransactionHistoryScreenProps = {
  transactions: Transaction[];
  onEdit: (transaction: Transaction) => void;
  onDelete: (id: string) => void;
};

export default function TransactionHistoryScreen({
  transactions,
  onEdit,
  onDelete,
}: TransactionHistoryScreenProps) {
  const { theme } = useTheme();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  // Get unique categories with their emojis
  const categoryMap = new Map<string, string>();
  transactions.forEach(t => {
    if (!categoryMap.has(t.category)) {
      categoryMap.set(t.category, t.emoji);
    }
  });
  const categories = Array.from(categoryMap.entries());

  const filteredTransactions = transactions.filter(t => {
    const matchesSearch = t.recipient.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         t.note.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         t.category.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = !selectedCategory || t.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  const handleDelete = (id: string) => {
    onDelete(id);
    toast.success('Transaction deleted');
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-200'} px-5 py-4 border-b`}>
        <h1 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Your Transactions</h1>
        
        {/* Search Bar */}
        <div className="relative mb-3">
          <Search className={`absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by name or category"
            className={`w-full pl-12 pr-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white placeholder:text-zinc-500' : 'border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all`}
          />
        </div>

        {/* Filter Pills */}
        <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
          <button
            onClick={() => setSelectedCategory(null)}
            className={`px-4 py-2 rounded-xl flex items-center gap-2 whitespace-nowrap transition-all flex-shrink-0 ${
              !selectedCategory
                ? 'bg-[#4F46E5] text-white'
                : `${theme === 'dark' ? 'bg-zinc-700 text-zinc-300 hover:bg-zinc-600' : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'}`
            }`}
          >
            <Filter className="w-4 h-4" />
            <span className="text-sm">All</span>
          </button>
          {categories.map(([categoryName, emoji]) => (
            <button
              key={categoryName}
              onClick={() => setSelectedCategory(categoryName === selectedCategory ? null : categoryName)}
              className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all flex-shrink-0 ${
                selectedCategory === categoryName
                  ? 'bg-[#4F46E5] text-white'
                  : `${theme === 'dark' ? 'bg-zinc-700 hover:bg-zinc-600' : 'bg-zinc-100 hover:bg-zinc-200'}`
              }`}
              title={categoryName}
            >
              <span className="text-xl">{emoji}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Transaction List */}
      <div className="flex-1 overflow-auto p-5">
        {filteredTransactions.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full py-12">
            <div className={`w-24 h-24 ${theme === 'dark' ? 'bg-zinc-800' : 'bg-zinc-100'} rounded-full flex items-center justify-center mb-4`}>
              <FileText className={`w-12 h-12 ${theme === 'dark' ? 'text-zinc-600' : 'text-zinc-400'}`} />
            </div>
            <h3 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>No transactions found</h3>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} text-center px-8`}>
              {searchQuery || selectedCategory
                ? 'Try adjusting your search or filters'
                : 'Upload a payment screenshot to add your first transaction'}
            </p>
          </div>
        ) : (
          <div className="space-y-3 pb-4">
            {/* Group by date */}
            {(() => {
              const groupedByDate: { [key: string]: Transaction[] } = {};
              filteredTransactions.forEach(t => {
                const dateKey = t.date.toLocaleDateString('en-IN', {
                  day: 'numeric',
                  month: 'long',
                  year: 'numeric',
                });
                if (!groupedByDate[dateKey]) {
                  groupedByDate[dateKey] = [];
                }
                groupedByDate[dateKey].push(t);
              });

              return Object.entries(groupedByDate).map(([date, txns]) => (
                <div key={date} className="mb-6">
                  <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-3 px-1`}>{date}</p>
                  <div className="space-y-2">
                    {txns.map((transaction) => (
                      <div
                        key={transaction.id}
                        className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-2xl p-4 shadow-sm border hover:shadow-md transition-all`}
                      >
                        <div className="flex items-start justify-between gap-3 mb-3">
                          <div className="flex items-start gap-3 flex-1 min-w-0">
                            <div className="w-12 h-12 bg-gradient-to-br from-indigo-50 to-purple-50 rounded-2xl flex items-center justify-center flex-shrink-0">
                              <span className="text-2xl">{transaction.emoji}</span>
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 mb-1">
                                <span className="text-xs bg-indigo-50 text-[#4F46E5] px-2 py-0.5 rounded-lg">
                                  {transaction.category}
                                </span>
                              </div>
                              <p className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>{transaction.recipient}</p>
                              {transaction.note && (
                                <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} truncate`}>{transaction.note}</p>
                              )}
                              <div className="flex items-center gap-2 mt-2">
                                <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>
                                  {transaction.date.toLocaleDateString('en-IN', {
                                    day: 'numeric',
                                    month: 'short',
                                  })}, {transaction.date.toLocaleTimeString('en-IN', {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                  })}
                                </p>
                                <span className={`text-xs ${theme === 'dark' ? 'text-zinc-600' : 'text-zinc-300'}`}>•</span>
                                <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>{transaction.bankInfo}</p>
                              </div>
                            </div>
                          </div>
                          <div className="text-right flex-shrink-0">
                            <p className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>₹{transaction.amount.toLocaleString('en-IN')}</p>
                          </div>
                        </div>
                        
                        {/* Action Buttons */}
                        <div className={`flex gap-2 pt-3 border-t ${theme === 'dark' ? 'border-zinc-700' : 'border-zinc-100'}`}>
                          <button
                            onClick={() => onEdit(transaction)}
                            className="flex-1 px-4 py-2 rounded-xl bg-indigo-50 text-[#4F46E5] hover:bg-indigo-100 transition-colors flex items-center justify-center gap-2"
                          >
                            <Edit2 className="w-4 h-4" />
                            <span className="text-sm">Edit</span>
                          </button>
                          <button
                            onClick={() => handleDelete(transaction.id)}
                            className="flex-1 px-4 py-2 rounded-xl bg-red-50 text-red-500 hover:bg-red-100 transition-colors flex items-center justify-center gap-2"
                          >
                            <Trash2 className="w-4 h-4" />
                            <span className="text-sm">Delete</span>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ));
            })()}
          </div>
        )}
      </div>
    </div>
  );
}