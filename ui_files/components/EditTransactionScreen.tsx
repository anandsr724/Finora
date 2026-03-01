import { useState } from 'react';
import { Transaction, Category } from '../App';
import { ArrowLeft, Plus, Calendar, CreditCard, ChevronDown, X, Check } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { useTheme } from '../contexts/ThemeContext';
import { getIcon, availableIcons } from '../utils/iconMapping';

type EditTransactionScreenProps = {
  transaction: Transaction;
  categories: Category[];
  onSave: (transaction: Transaction) => void;
  onCancel: () => void;
  onAddCategory: (category: Category) => void;
};

export default function EditTransactionScreen({
  transaction,
  categories,
  onSave,
  onCancel,
  onAddCategory,
}: EditTransactionScreenProps) {
  const { theme } = useTheme();
  const [formData, setFormData] = useState(transaction);
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [newCategoryEmoji, setNewCategoryEmoji] = useState('Utensils');

  const handleSubmit = () => {
    if (!formData.amount || !formData.recipient) {
      toast.error('Please fill in required fields');
      return;
    }
    onSave(formData);
    toast.success('Transaction saved successfully');
  };

  const handleAddNewCategory = () => {
    if (!newCategoryName.trim()) {
      toast.error('Please enter a category name');
      return;
    }
    const newCategory: Category = {
      id: Date.now().toString(),
      name: newCategoryName,
      emoji: newCategoryEmoji,
      isCustom: true,
    };
    onAddCategory(newCategory);
    setFormData({ ...formData, category: newCategory.name, emoji: newCategory.emoji });
    setShowAddCategory(false);
    setNewCategoryName('');
    setNewCategoryEmoji('Utensils');
    toast.success('Category added successfully');
  };

  return (
    <div className={`flex flex-col h-full ${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F8F9FB]'}`}>
      {/* Header */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-200'} px-5 py-4 flex items-center gap-3 border-b`}>
        <button
          onClick={onCancel}
          className={`w-10 h-10 rounded-xl ${theme === 'dark' ? 'bg-zinc-700 hover:bg-zinc-600' : 'bg-zinc-100 hover:bg-zinc-200'} flex items-center justify-center transition-colors active:scale-95`}
        >
          <ArrowLeft className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-700'}`} />
        </button>
        <div>
          <h1 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Edit Transaction</h1>
          <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>Update transaction details</p>
        </div>
      </div>

      {/* Form */}
      <div className="flex-1 overflow-auto p-5 pb-24">
        <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border space-y-5`}>
          {/* Amount */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Amount *</label>
            <div className="relative">
              <span className={`absolute left-4 top-1/2 -translate-y-1/2 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>₹</span>
              <input
                type="number"
                value={formData.amount || ''}
                onChange={(e) => setFormData({ ...formData, amount: parseFloat(e.target.value) || 0 })}
                placeholder="0.00"
                className={`w-full pl-10 pr-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white placeholder:text-zinc-600' : 'border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all`}
              />
            </div>
          </div>

          {/* Recipient */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Recipient *</label>
            <input
              type="text"
              value={formData.recipient}
              onChange={(e) => setFormData({ ...formData, recipient: e.target.value })}
              placeholder="Enter recipient name"
              className={`w-full px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white placeholder:text-zinc-600' : 'border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all`}
            />
          </div>

          {/* Note */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Note</label>
            <textarea
              value={formData.note}
              onChange={(e) => setFormData({ ...formData, note: e.target.value })}
              placeholder="Add a note (optional)"
              rows={3}
              className={`w-full px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white placeholder:text-zinc-600' : 'border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all resize-none`}
            />
          </div>

          {/* Category */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Category</label>
            <button
              onClick={() => setShowCategoryModal(true)}
              className={`w-full px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white' : 'bg-white border-zinc-200'} transition-all flex items-center justify-between`}
            >
              <div className="flex items-center gap-3">
                {(() => {
                  const IconComponent = getIcon(formData.emoji);
                  return <IconComponent className="w-5 h-5 text-[#6B5DD3]" />;
                })()}
                <span className="text-sm">{formData.category || 'Select category'}</span>
              </div>
              <ChevronDown className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`} />
            </button>
          </div>

          {/* Category Selection Modal */}
          {showCategoryModal && (
            <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center sm:justify-center">
              <div 
                className="absolute inset-0" 
                onClick={() => setShowCategoryModal(false)}
              />
              <div className={`relative w-full sm:max-w-md ${
                theme === 'dark' ? 'bg-zinc-900' : 'bg-white'
              } rounded-t-3xl sm:rounded-3xl shadow-xl max-h-[80vh] flex flex-col`}>
                {/* Modal Header */}
                <div className={`flex items-center justify-between p-6 border-b ${
                  theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'
                }`}>
                  <h2 className={`text-xl font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                    Select Category
                  </h2>
                  <button
                    onClick={() => setShowCategoryModal(false)}
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
                      const isSelected = formData.category === category.name;
                      
                      return (
                        <button
                          key={category.id}
                          onClick={() => {
                            setFormData({ ...formData, category: category.name, emoji: category.icon });
                            setShowCategoryModal(false);
                          }}
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
                  <button
                    onClick={() => {
                      setShowCategoryModal(false);
                      setShowAddCategory(true);
                    }}
                    className={`w-full px-4 py-3 rounded-2xl border-2 border-dashed ${theme === 'dark' ? 'border-zinc-700 hover:border-[#6B5DD3] text-zinc-400' : 'border-zinc-300 hover:border-[#6B5DD3] text-zinc-600'} hover:text-[#6B5DD3] transition-all flex items-center justify-center gap-2`}
                  >
                    <Plus className="w-4 h-4" />
                    <span className="text-sm font-medium">Create New Category</span>
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Add Category Modal */}
          {showAddCategory && (
            <div className={`p-4 ${theme === 'dark' ? 'bg-purple-900/30 border-purple-800' : 'bg-purple-50 border-purple-200'} rounded-2xl border space-y-3`}>
              <h3 className={`text-sm font-medium ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>Create New Category</h3>
              <div>
                <label className={`block text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Icon</label>
                <div className="grid grid-cols-8 gap-2">
                  {availableIcons.slice(0, 16).map((iconName) => {
                    const IconComponent = getIcon(iconName);
                    return (
                      <button
                        key={iconName}
                        onClick={() => setNewCategoryEmoji(iconName)}
                        className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all ${
                          newCategoryEmoji === iconName
                            ? `${theme === 'dark' ? 'bg-zinc-700' : 'bg-white'} ring-2 ring-[#6B5DD3] scale-110`
                            : `${theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-white/50 hover:bg-white'}`
                        }`}
                      >
                        <IconComponent className={`w-5 h-5 ${newCategoryEmoji === iconName ? 'text-[#6B5DD3]' : theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'}`} />
                      </button>
                    );
                  })}
                </div>
              </div>
              <div>
                <label className={`block text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Category Name</label>
                <input
                  type="text"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="e.g., Subscriptions"
                  className={`w-full px-3 py-2 rounded-xl border ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700 text-white' : 'bg-white border-purple-200'} focus:border-[#6B5DD3] focus:outline-none focus:ring-2 focus:ring-[#6B5DD3]/20`}
                />
              </div>
              <div className="flex gap-2">
                <button
                  onClick={handleAddNewCategory}
                  className="flex-1 px-4 py-2 rounded-xl bg-[#6B5DD3] text-white hover:bg-[#5B4DC3] transition-colors font-medium"
                >
                  Add
                </button>
                <button
                  onClick={() => {
                    setShowAddCategory(false);
                    setNewCategoryName('');
                    setNewCategoryEmoji('Utensils');
                  }}
                  className={`flex-1 px-4 py-2 rounded-xl ${theme === 'dark' ? 'bg-zinc-700 text-zinc-300' : 'bg-white text-zinc-600'} hover:bg-zinc-100 transition-colors`}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Date & Time */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Date & Time</label>
            <div className="relative">
              <Calendar className={`absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`} />
              <input
                type="datetime-local"
                value={formData.date.toISOString().slice(0, 16)}
                onChange={(e) => setFormData({ ...formData, date: new Date(e.target.value) })}
                className={`w-full pl-12 pr-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white' : 'border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all`}
              />
            </div>
          </div>

          {/* Transaction ID */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Transaction ID</label>
            <input
              type="text"
              value={formData.transactionId}
              onChange={(e) => setFormData({ ...formData, transactionId: e.target.value })}
              placeholder="UPI/XXXXXXXXXXXX"
              className={`w-full px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white placeholder:text-zinc-600' : 'border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all`}
            />
          </div>

          {/* Bank Info */}
          <div>
            <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Payment Method</label>
            <div className="relative">
              <CreditCard className={`absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`} />
              <select
                value={formData.bankInfo}
                onChange={(e) => setFormData({ ...formData, bankInfo: e.target.value })}
                className={`w-full pl-12 pr-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-900 border-zinc-700 text-white' : 'bg-white border-zinc-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20 transition-all appearance-none`}
              >
                <option>Google Pay</option>
                <option>PhonePe</option>
                <option>ICICI Bank</option>
                <option>HDFC Bank</option>
                <option>Paytm</option>
                <option>Other</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-200'} border-t px-5 py-4 flex gap-3`}>
        <button
          onClick={onCancel}
          className={`flex-1 px-6 py-3 rounded-2xl border ${theme === 'dark' ? 'border-zinc-700 text-zinc-300 hover:bg-zinc-700' : 'border-zinc-200 text-zinc-700 hover:bg-zinc-50'} transition-all active:scale-[0.98]`}
        >
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          className="flex-1 px-6 py-3 rounded-2xl bg-gradient-to-r from-[#4F46E5] to-[#6C63FF] text-white shadow-lg shadow-indigo-500/30 hover:shadow-xl hover:shadow-indigo-500/40 transition-all active:scale-[0.98]"
        >
          Save
        </button>
      </div>
    </div>
  );
}