import { useState } from 'react';
import { Transaction, Category } from '../App';
import { Search, Edit2, Trash2, FileText, X, Check, Calendar, ChevronDown, MoreHorizontal } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { useTheme } from '../contexts/ThemeContext';
import { getIcon } from '../utils/iconMapping';

type TransactionHistoryScreenProps = {
  transactions: Transaction[];
  categories: Category[];
  onEdit: (transaction: Transaction) => void;
  onDelete: (id: string) => void;
};

export default function TransactionHistoryScreen({
  transactions,
  categories,
  onEdit,
  onDelete,
}: TransactionHistoryScreenProps) {
  const { theme } = useTheme();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [showFilterModal, setShowFilterModal] = useState(false);
  const [selectedMonth, setSelectedMonth] = useState<string>('all');
  const [showMonthDropdown, setShowMonthDropdown] = useState(false);
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);

  // Get unique categories with their icons
  const categoryMap = new Map<string, string>();
  transactions.forEach(t => {
    if (!categoryMap.has(t.category)) {
      categoryMap.set(t.category, t.emoji);
    }
  });
  
  // Get recent categories (most frequently used, up to 4 to make room for More button)
  const categoryCounts = new Map<string, number>();
  transactions.forEach(t => {
    categoryCounts.set(t.category, (categoryCounts.get(t.category) || 0) + 1);
  });
  const recentCategories = Array.from(categoryCounts.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 4)
    .map(([cat]) => cat);

  const toggleCategoryFilter = (categoryName: string) => {
    setSelectedCategories(prev => 
      prev.includes(categoryName)
        ? prev.filter(c => c !== categoryName)
        : [...prev, categoryName]
    );
  };

  const clearFilters = () => {
    setSelectedCategories([]);
  };

  // Generate month options
  const monthOptions = [
    { value: 'all', label: 'All Time' },
    { value: '2026-03', label: 'March 2026' },
    { value: '2026-02', label: 'February 2026' },
    { value: '2026-01', label: 'January 2026' },
    { value: '2025-12', label: 'December 2025' },
    { value: '2025-11', label: 'November 2025' },
    { value: '2025-10', label: 'October 2025' },
  ];

  const filteredTransactions = transactions.filter(t => {
    const matchesSearch = t.recipient.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         t.note.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         t.category.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = !selectedCategories.length || selectedCategories.includes(t.category);
    
    // Month filter
    const matchesMonth = selectedMonth === 'all' || 
      t.date.toISOString().slice(0, 7) === selectedMonth;
    
    return matchesSearch && matchesCategory && matchesMonth;
  });

  const handleDelete = (id: string) => {
    onDelete(id);
    setSelectedTransaction(null);
    toast.success('Transaction deleted');
  };

  const handleEdit = (transaction: Transaction) => {
    onEdit(transaction);
    setSelectedTransaction(null);
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className={`${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F5F5F7]'} px-6 py-6 border-b ${theme === 'dark' ? 'border-zinc-800' : 'border-transparent'}`}>
        <h1 className={`text-2xl font-bold mb-4 ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>Transactions</h1>
        
        {/* Search Bar */}
        <div className="relative mb-4">
          <Search className={`absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search transactions..."
            className={`w-full pl-12 pr-4 py-3.5 rounded-2xl border-0 ${theme === 'dark' ? 'bg-zinc-800 text-white placeholder:text-zinc-500' : 'bg-white placeholder:text-zinc-400 shadow-sm'} focus:outline-none focus:ring-2 focus:ring-[#6B5DD3]/20 transition-all`}
          />
        </div>

        {/* Month Selector */}
        <div className="relative mb-4">
          <button
            onClick={() => setShowMonthDropdown(!showMonthDropdown)}
            className={`w-full flex items-center gap-3 px-4 py-3.5 rounded-2xl ${
              theme === 'dark' ? 'bg-zinc-800 text-white' : 'bg-white shadow-sm'
            } transition-all`}
          >
            <Calendar className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`} />
            <span className="flex-1 text-left text-sm">
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

        {/* Filter Pills - Static (No Scroll) */}
        <div className="grid grid-cols-6 gap-2">
          <button
            onClick={clearFilters}
            className={`col-span-1 py-2.5 rounded-2xl flex items-center justify-center whitespace-nowrap transition-all ${
              selectedCategories.length === 0
                ? 'bg-[#6B5DD3] text-white shadow-md'
                : `${theme === 'dark' ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700' : 'bg-white text-zinc-600 hover:bg-gray-50 shadow-sm'}`
            }`}
          >
            <span className="text-xs font-medium">All</span>
          </button>
          
          {/* Recent category quick filters (4 categories) */}
          {recentCategories.map(categoryName => {
            const emoji = categoryMap.get(categoryName);
            if (!emoji) return null;
            const IconComponent = getIcon(emoji);
            const isSelected = selectedCategories.includes(categoryName);
            
            return (
              <button
                key={categoryName}
                onClick={() => toggleCategoryFilter(categoryName)}
                className={`col-span-1 py-2.5 rounded-2xl flex items-center justify-center transition-all relative ${
                  isSelected
                    ? 'bg-[#6B5DD3] shadow-md'
                    : `${theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-white hover:bg-gray-50 shadow-sm'}`
                }`}
                title={categoryName}
              >
                <IconComponent className={`w-5 h-5 ${isSelected ? 'text-white' : 'text-[#6B5DD3]'}`} />
                {isSelected && (
                  <div className="absolute -top-1 -right-1 w-4 h-4 bg-green-500 rounded-full flex items-center justify-center">
                    <Check className="w-2.5 h-2.5 text-white" />
                  </div>
                )}
              </button>
            );
          })}
          
          {/* More filters button with 3 dots */}
          <button
            onClick={() => setShowFilterModal(true)}
            className={`col-span-1 py-2.5 rounded-2xl flex items-center justify-center transition-all relative ${
              theme === 'dark' ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700' : 'bg-white text-zinc-600 hover:bg-gray-50 shadow-sm'
            }`}
          >
            <MoreHorizontal className="w-5 h-5" />
            {selectedCategories.length > 0 && (
              <div className="absolute -top-1 -right-1 w-5 h-5 bg-[#6B5DD3] text-white rounded-full text-xs flex items-center justify-center font-medium">
                {selectedCategories.length}
              </div>
            )}
          </button>
        </div>
      </div>

      {/* Filter Modal */}
      {showFilterModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center sm:justify-center">
          <div 
            className="absolute inset-0" 
            onClick={() => setShowFilterModal(false)}
          />
          <div className={`relative w-full sm:max-w-md ${
            theme === 'dark' ? 'bg-zinc-900' : 'bg-white'
          } rounded-t-3xl sm:rounded-3xl shadow-xl max-h-[80vh] flex flex-col`}>
            {/* Modal Header */}
            <div className={`flex items-center justify-between p-6 border-b ${
              theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'
            }`}>
              <h2 className={`text-xl font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                Filter Categories
              </h2>
              <button
                onClick={() => setShowFilterModal(false)}
                className={`w-10 h-10 rounded-xl ${
                  theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-zinc-100 hover:bg-zinc-200'
                } flex items-center justify-center transition-colors`}
              >
                <X className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'}`} />
              </button>
            </div>

            {/* Category List */}
            <div className="flex-1 overflow-y-auto p-6">
              <div className="grid grid-cols-2 gap-3">
                {categories.map(category => {
                  const IconComponent = getIcon(category.icon);
                  const isSelected = selectedCategories.includes(category.name);
                  
                  return (
                    <button
                      key={category.id}
                      onClick={() => toggleCategoryFilter(category.name)}
                      className={`p-4 rounded-2xl border-2 transition-all ${
                        isSelected
                          ? 'border-[#6B5DD3] bg-[#6B5DD3]/10'
                          : theme === 'dark'
                            ? 'border-zinc-800 bg-zinc-800 hover:border-zinc-700'
                            : 'border-zinc-200 bg-white hover:border-zinc-300'
                      }`}
                    >
                      <div className="flex flex-col items-center gap-2">
                        <div className={`w-14 h-14 rounded-2xl flex items-center justify-center ${
                          isSelected 
                            ? 'bg-[#6B5DD3]' 
                            : 'bg-gradient-to-br from-purple-50 to-indigo-50'
                        }`}>
                          <IconComponent className={`w-7 h-7 ${
                            isSelected ? 'text-white' : 'text-[#6B5DD3]'
                          }`} />
                        </div>
                        <span className={`text-sm font-medium text-center ${
                          isSelected
                            ? 'text-[#6B5DD3]'
                            : theme === 'dark'
                              ? 'text-zinc-300'
                              : 'text-zinc-700'
                        }`}>
                          {category.name}
                        </span>
                        {isSelected && (
                          <div className="w-6 h-6 bg-green-500 rounded-full flex items-center justify-center">
                            <Check className="w-4 h-4 text-white" />
                          </div>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Modal Footer */}
            <div className={`p-6 border-t ${theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'}`}>
              <div className="flex gap-3">
                <button
                  onClick={clearFilters}
                  className={`flex-1 px-4 py-3 rounded-2xl transition-colors ${
                    theme === 'dark'
                      ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
                      : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
                  }`}
                >
                  Clear All
                </button>
                <button
                  onClick={() => setShowFilterModal(false)}
                  className="flex-1 px-4 py-3 rounded-2xl bg-[#6B5DD3] text-white hover:bg-[#5B4DC3] transition-colors"
                >
                  Apply ({selectedCategories.length})
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Transaction Detail Modal */}
      {selectedTransaction && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center sm:justify-center">
          <div 
            className="absolute inset-0" 
            onClick={() => setSelectedTransaction(null)}
          />
          <div className={`relative w-full sm:max-w-md ${
            theme === 'dark' ? 'bg-zinc-900' : 'bg-white'
          } rounded-t-3xl sm:rounded-3xl shadow-xl`}>
            {/* Modal Header */}
            <div className={`flex items-center justify-between p-6 border-b ${
              theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'
            }`}>
              <h2 className={`text-xl font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                Transaction Details
              </h2>
              <button
                onClick={() => setSelectedTransaction(null)}
                className={`w-10 h-10 rounded-xl ${
                  theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-zinc-100 hover:bg-zinc-200'
                } flex items-center justify-center transition-colors`}
              >
                <X className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'}`} />
              </button>
            </div>

            {/* Transaction Info */}
            <div className="p-6">
              {(() => {
                const IconComponent = getIcon(selectedTransaction.emoji);
                return (
                  <>
                    <div className="flex items-center gap-4 mb-6">
                      <div className="w-16 h-16 bg-gradient-to-br from-purple-50 to-indigo-50 rounded-2xl flex items-center justify-center">
                        <IconComponent className="w-8 h-8 text-[#6B5DD3]" />
                      </div>
                      <div className="flex-1">
                        <p className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                          ₹{selectedTransaction.amount.toLocaleString('en-IN')}
                        </p>
                        <span className="text-xs bg-purple-50 text-[#6B5DD3] px-2 py-1 rounded-lg font-medium">
                          {selectedTransaction.category}
                        </span>
                      </div>
                    </div>

                    <div className="space-y-4">
                      <div>
                        <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mb-1`}>Recipient</p>
                        <p className={`text-sm font-medium ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                          {selectedTransaction.recipient}
                        </p>
                      </div>

                      {selectedTransaction.note && (
                        <div>
                          <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mb-1`}>Note</p>
                          <p className={`text-sm ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-600'}`}>
                            {selectedTransaction.note}
                          </p>
                        </div>
                      )}

                      <div>
                        <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mb-1`}>Date & Time</p>
                        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-600'}`}>
                          {selectedTransaction.date.toLocaleDateString('en-IN', {
                            day: 'numeric',
                            month: 'long',
                            year: 'numeric',
                          })} • {selectedTransaction.date.toLocaleTimeString('en-IN', {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </p>
                      </div>

                      <div>
                        <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mb-1`}>Payment Method</p>
                        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-600'}`}>
                          {selectedTransaction.bankInfo}
                        </p>
                      </div>

                      <div>
                        <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mb-1`}>Transaction ID</p>
                        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-600'} font-mono`}>
                          {selectedTransaction.transactionId}
                        </p>
                      </div>
                    </div>
                  </>
                );
              })()}
            </div>

            {/* Action Buttons */}
            <div className={`p-6 border-t ${theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'} flex gap-3`}>
              <button
                onClick={() => handleEdit(selectedTransaction)}
                className="flex-1 px-4 py-3 rounded-2xl bg-purple-50 text-[#6B5DD3] hover:bg-purple-100 transition-colors flex items-center justify-center gap-2"
              >
                <Edit2 className="w-5 h-5" />
                <span className="font-medium">Edit</span>
              </button>
              <button
                onClick={() => handleDelete(selectedTransaction.id)}
                className="flex-1 px-4 py-3 rounded-2xl bg-red-50 text-red-500 hover:bg-red-100 transition-colors flex items-center justify-center gap-2"
              >
                <Trash2 className="w-5 h-5" />
                <span className="font-medium">Delete</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Transaction List */}
      <div className={`flex-1 overflow-auto px-6 py-4 ${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F5F5F7]'}`}>
        {filteredTransactions.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full py-12">
            <div className={`w-24 h-24 ${theme === 'dark' ? 'bg-zinc-800' : 'bg-zinc-100'} rounded-full flex items-center justify-center mb-4`}>
              <FileText className={`w-12 h-12 ${theme === 'dark' ? 'text-zinc-600' : 'text-zinc-400'}`} />
            </div>
            <h3 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>No transactions found</h3>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} text-center px-8`}>
              {searchQuery || selectedCategories.length > 0
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
                    {txns.map((transaction) => {
                      const IconComponent = getIcon(transaction.emoji);
                      return (
                        <button
                          key={transaction.id}
                          onClick={() => setSelectedTransaction(transaction)}
                          className={`w-full ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-2xl p-4 shadow-sm border hover:shadow-md transition-all text-left`}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex items-start gap-3 flex-1 min-w-0">
                              <div className="w-12 h-12 bg-gradient-to-br from-purple-50 to-indigo-50 rounded-2xl flex items-center justify-center flex-shrink-0">
                                <IconComponent className="w-6 h-6 text-[#6B5DD3]" />
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 mb-1">
                                  <span className="text-xs bg-purple-50 text-[#6B5DD3] px-2 py-0.5 rounded-lg font-medium">
                                    {transaction.category}
                                  </span>
                                </div>
                                <p className={`font-medium ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>{transaction.recipient}</p>
                                {transaction.note && (
                                  <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} truncate`}>{transaction.note}</p>
                                )}
                                <div className="flex items-center gap-2 mt-2">
                                  <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>
                                    {transaction.date.toLocaleTimeString('en-IN', {
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
                              <p className={`font-semibold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>₹{transaction.amount.toLocaleString('en-IN')}</p>
                            </div>
                          </div>
                        </button>
                      );
                    })}
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