import { useState } from 'react';
import { Transaction, Category } from '../App';
import { ArrowLeft, Plus, Calendar, CreditCard } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { useTheme } from '../contexts/ThemeContext';

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
  const [newCategoryName, setNewCategoryName] = useState('');
  const [newCategoryEmoji, setNewCategoryEmoji] = useState('📦');

  const emojiOptions = ['🍕', '🛍️', '🚗', '💡', '🎬', '🏥', '📚', '✈️', '🎮', '💰', '🏠', '👔', '🎁', '☕', '🍔', '📱', '💻', '🎵', '🏋️', '🐕'];

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
    setNewCategoryEmoji('📦');
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
            <div className="grid grid-cols-2 gap-2 mb-2">
              {categories.map((cat) => (
                <button
                  key={cat.id}
                  onClick={() => setFormData({ ...formData, category: cat.name, emoji: cat.emoji })}
                  className={`px-4 py-3 rounded-2xl border transition-all flex items-center gap-2 ${
                    formData.category === cat.name
                      ? 'border-[#4F46E5] bg-indigo-50 text-[#4F46E5]'
                      : `${theme === 'dark' ? 'border-zinc-700 hover:border-zinc-600 text-zinc-300' : 'border-zinc-200 hover:border-zinc-300 text-zinc-700'}`
                  }`}
                >
                  <span className="text-xl">{cat.emoji}</span>
                  <span className="text-sm truncate">{cat.name}</span>
                </button>
              ))}
            </div>
            <button
              onClick={() => setShowAddCategory(!showAddCategory)}
              className={`w-full px-4 py-3 rounded-2xl border-2 border-dashed ${theme === 'dark' ? 'border-zinc-700 hover:border-[#4F46E5] text-zinc-400' : 'border-zinc-300 hover:border-[#4F46E5] text-zinc-600'} hover:text-[#4F46E5] transition-all flex items-center justify-center gap-2`}
            >
              <Plus className="w-4 h-4" />
              <span className="text-sm">New Category</span>
            </button>
          </div>

          {/* Add Category Modal */}
          {showAddCategory && (
            <div className={`p-4 ${theme === 'dark' ? 'bg-indigo-900/30 border-indigo-800' : 'bg-indigo-50 border-indigo-200'} rounded-2xl border space-y-3`}>
              <h3 className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>Create New Category</h3>
              <div>
                <label className={`block text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Emoji</label>
                <div className="grid grid-cols-10 gap-1">
                  {emojiOptions.map((emoji) => (
                    <button
                      key={emoji}
                      onClick={() => setNewCategoryEmoji(emoji)}
                      className={`w-8 h-8 rounded-lg flex items-center justify-center transition-all ${
                        newCategoryEmoji === emoji
                          ? `${theme === 'dark' ? 'bg-zinc-700' : 'bg-white'} ring-2 ring-[#4F46E5] scale-110`
                          : `${theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-white/50 hover:bg-white'}`
                      }`}
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className={`block text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Category Name</label>
                <input
                  type="text"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="e.g., Subscriptions"
                  className={`w-full px-3 py-2 rounded-xl border ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700 text-white' : 'bg-white border-indigo-200'} focus:border-[#4F46E5] focus:outline-none focus:ring-2 focus:ring-[#4F46E5]/20`}
                />
              </div>
              <div className="flex gap-2">
                <button
                  onClick={handleAddNewCategory}
                  className="flex-1 px-4 py-2 rounded-xl bg-[#4F46E5] text-white hover:bg-[#4338CA] transition-colors"
                >
                  Add
                </button>
                <button
                  onClick={() => {
                    setShowAddCategory(false);
                    setNewCategoryName('');
                    setNewCategoryEmoji('📦');
                  }}
                  className="flex-1 px-4 py-2 rounded-xl bg-white text-zinc-600 hover:bg-zinc-100 transition-colors"
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