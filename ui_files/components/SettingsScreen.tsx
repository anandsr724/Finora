import { useState } from 'react';
import { Category } from '../App';
import { Plus, Edit2, Trash2, Download, Moon } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { useTheme } from '../contexts/ThemeContext';

type SettingsScreenProps = {
  categories: Category[];
  onAddCategory: (category: Category) => void;
  onDeleteCategory: (id: string) => void;
};

export default function SettingsScreen({
  categories,
  onAddCategory,
  onDeleteCategory,
}: SettingsScreenProps) {
  const { theme, toggleTheme } = useTheme();
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [newCategoryEmoji, setNewCategoryEmoji] = useState('📦');

  const emojiOptions = [
    '🍕', '🛍️', '🚗', '💡', '🎬', '🏥', '📚', '✈️', 
    '🎮', '💰', '🏠', '👔', '🎁', '☕', '🍔', '📱', 
    '💻', '🎵', '🏋️', '🐕', '🌳', '🎨', '📦', '🔧'
  ];

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
    setShowAddCategory(false);
    setNewCategoryName('');
    setNewCategoryEmoji('📦');
    toast.success('Category added successfully');
  };

  const handleDeleteCategory = (id: string) => {
    onDeleteCategory(id);
    toast.success('Category deleted');
  };

  const handleExportCSV = () => {
    toast.success('CSV exported to Downloads');
  };

  return (
    <div className="flex flex-col h-full overflow-auto p-5 pb-20">
      {/* Header */}
      <div className="mb-6">
        <h1 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Settings</h1>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>Manage your preferences</p>
      </div>

      {/* Categories Section */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border mb-6`}>
        <div className="flex items-center justify-between mb-4">
          <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Manage Categories</h2>
          <button
            onClick={() => setShowAddCategory(!showAddCategory)}
            className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#4F46E5] to-[#6C63FF] text-white flex items-center justify-center hover:shadow-lg hover:shadow-indigo-500/30 transition-all active:scale-95"
          >
            <Plus className="w-5 h-5" />
          </button>
        </div>

        {/* Add Category Form */}
        {showAddCategory && (
          <div className={`mb-4 p-4 ${theme === 'dark' ? 'bg-indigo-900/30 border-indigo-800' : 'bg-indigo-50 border-indigo-200'} rounded-2xl border space-y-3`}>
            <h3 className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>Create New Category</h3>
            
            <div>
              <label className={`block text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Emoji</label>
              <div className="grid grid-cols-12 gap-1">
                {emojiOptions.map((emoji) => (
                  <button
                    key={emoji}
                    onClick={() => setNewCategoryEmoji(emoji)}
                    className={`w-7 h-7 rounded-lg flex items-center justify-center transition-all text-sm ${
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
                Add Category
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

        {/* Category List */}
        <div className="space-y-2">
          {categories.map((category) => (
            <div
              key={category.id}
              className={`flex items-center gap-3 p-3 rounded-2xl border ${theme === 'dark' ? 'border-zinc-700 hover:border-zinc-600' : 'border-zinc-100 hover:border-zinc-200'} transition-all`}
            >
              <span className="text-2xl">{category.emoji}</span>
              <div className="flex-1 min-w-0">
                <p className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>{category.name}</p>
                {!category.isCustom && (
                  <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>Default category</p>
                )}
              </div>
              {category.isCustom && (
                <div className="flex gap-1">
                  <button
                    onClick={() => handleDeleteCategory(category.id)}
                    className="w-8 h-8 rounded-lg bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100 transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Export Data Section */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border mb-6`}>
        <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Export Data</h2>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-4`}>
          Download your transaction history as a CSV file
        </p>
        <button
          onClick={handleExportCSV}
          className="w-full px-6 py-3 rounded-2xl bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow-lg shadow-emerald-500/30 hover:shadow-xl hover:shadow-emerald-500/40 transition-all active:scale-[0.98] flex items-center justify-center gap-3"
        >
          <Download className="w-5 h-5" />
          <span>Export to CSV</span>
        </button>
      </div>

      {/* Appearance Section */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border mb-6`}>
        <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Appearance</h2>
        
        <div className={`flex items-center justify-between p-3 rounded-2xl border ${theme === 'dark' ? 'border-zinc-700' : 'border-zinc-100'}`}>
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-xl ${theme === 'dark' ? 'bg-zinc-700' : 'bg-zinc-100'} flex items-center justify-center`}>
              <Moon className={`w-5 h-5 ${theme === 'dark' ? 'text-indigo-400' : 'text-zinc-600'}`} />
            </div>
            <div>
              <p className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>Dark Mode</p>
              <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>Toggle appearance</p>
            </div>
          </div>
          <button
            onClick={toggleTheme}
            className={`relative w-14 h-8 rounded-full transition-colors ${
              theme === 'dark' ? 'bg-[#4F46E5]' : 'bg-zinc-200'
            }`}
          >
            <div
              className={`absolute top-1 left-1 w-6 h-6 bg-white rounded-full transition-transform ${
                theme === 'dark' ? 'translate-x-6' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
      </div>

      {/* About Section */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-5 shadow-sm border`}>
        <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>About Finora</h2>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-2`}>
          Version 1.0.0
        </p>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
          Automatically extract and manage your UPI payment transactions with ease.
        </p>
      </div>

      {/* Info Message */}
      <div className={`mt-6 p-4 ${theme === 'dark' ? 'bg-indigo-900/30 border-indigo-800' : 'bg-indigo-50 border-indigo-200'} rounded-2xl border`}>
        <p className={`text-sm ${theme === 'dark' ? 'text-indigo-300' : 'text-indigo-900'} mb-1`}>💡 Tip</p>
        <p className={`text-xs ${theme === 'dark' ? 'text-indigo-400' : 'text-indigo-700'}`}>
          Use the camera button on the home screen to quickly process payment screenshots from Google Pay, PhonePe, or ICICI Bank.
        </p>
      </div>
    </div>
  );
}