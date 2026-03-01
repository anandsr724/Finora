import { useState } from 'react';
import { Category } from '../App';
import { Plus, Edit2, Trash2, Download, Moon, X, Check, ChevronRight } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { useTheme } from '../contexts/ThemeContext';
import { getIcon, availableIcons } from '../utils/iconMapping';

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
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [newCategoryEmoji, setNewCategoryEmoji] = useState('Utensils');

  const handleAddNewCategory = () => {
    if (!newCategoryName.trim()) {
      toast.error('Please enter a category name');
      return;
    }
    const newCategory: Category = {
      id: Date.now().toString(),
      name: newCategoryName,
      icon: newCategoryEmoji,
      isCustom: true,
    };
    onAddCategory(newCategory);
    setShowAddCategory(false);
    setNewCategoryName('');
    setNewCategoryEmoji('Utensils');
    toast.success('Category added successfully');
  };

  const handleDeleteCategory = (id: string, name: string) => {
    onDeleteCategory(id);
    toast.success(`${name} category deleted`);
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
          <div>
            <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Categories</h2>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
              {categories.length} total
            </p>
          </div>
          <button
            onClick={() => setShowCategoryModal(true)}
            className={`px-4 py-2 rounded-2xl border ${theme === 'dark' ? 'border-zinc-700 text-zinc-300 hover:bg-zinc-700' : 'border-zinc-200 text-zinc-700 hover:bg-zinc-50'} flex items-center gap-2 transition-colors`}
          >
            <span className="text-sm">Manage</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>

        {/* Horizontal Scrollable Category Preview */}
        <div className="relative -mx-1">
          <div className="flex gap-3 overflow-x-auto pb-2 px-1 scrollbar-hide" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
            {categories.slice(0, 12).map((category) => {
              const IconComponent = getIcon(category.icon);
              return (
                <div
                  key={category.id}
                  className={`flex-shrink-0 px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'border-zinc-700 bg-zinc-700/50' : 'border-zinc-200 bg-zinc-50'} flex items-center gap-3 min-w-fit`}
                >
                  <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-purple-50 to-indigo-50 flex items-center justify-center flex-shrink-0">
                    <IconComponent className="w-5 h-5 text-[#6B5DD3]" />
                  </div>
                  <span className={`text-sm font-medium whitespace-nowrap ${theme === 'dark' ? 'text-zinc-300' : 'text-zinc-700'}`}>
                    {category.name}
                  </span>
                </div>
              );
            })}
            {categories.length > 12 && (
              <button
                onClick={() => setShowCategoryModal(true)}
                className={`flex-shrink-0 px-4 py-3 rounded-2xl border-2 border-dashed ${theme === 'dark' ? 'border-zinc-700 text-zinc-400' : 'border-zinc-300 text-zinc-600'} flex items-center gap-2 min-w-fit hover:border-[#6B5DD3] hover:text-[#6B5DD3] transition-colors`}
              >
                <span className="text-sm font-medium whitespace-nowrap">+{categories.length - 12} more</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Category Management Modal */}
      {showCategoryModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center sm:justify-center">
          <div 
            className="absolute inset-0" 
            onClick={() => setShowCategoryModal(false)}
          />
          <div className={`relative w-full sm:max-w-md ${
            theme === 'dark' ? 'bg-zinc-900' : 'bg-white'
          } rounded-t-3xl sm:rounded-3xl shadow-xl max-h-[85vh] flex flex-col`}>
            {/* Modal Header */}
            <div className={`flex items-center justify-between p-6 border-b ${
              theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'
            }`}>
              <div>
                <h2 className={`text-xl font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                  Manage Categories
                </h2>
                <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
                  {categories.length} total
                </p>
              </div>
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
              <div className="space-y-2">
                {categories.map((category) => {
                  const IconComponent = getIcon(category.icon);
                  return (
                    <div
                      key={category.id}
                      className={`flex items-center gap-3 p-4 rounded-2xl border ${theme === 'dark' ? 'border-zinc-800 bg-zinc-800' : 'border-zinc-200 bg-white'} transition-all`}
                    >
                      <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-purple-50 to-indigo-50 flex items-center justify-center flex-shrink-0">
                        <IconComponent className="w-6 h-6 text-[#6B5DD3]" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className={`text-sm font-medium ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>{category.name}</p>
                        {!category.isCustom && (
                          <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`}>Default</p>
                        )}
                      </div>
                      {category.isCustom && (
                        <button
                          onClick={() => handleDeleteCategory(category.id, category.name)}
                          className="w-9 h-9 rounded-xl bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100 transition-colors flex-shrink-0"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Modal Footer - Add New Category */}
            <div className={`p-6 border-t ${theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'}`}>
              <button
                onClick={() => {
                  setShowCategoryModal(false);
                  setShowAddCategory(true);
                }}
                className={`w-full px-4 py-3 rounded-2xl border-2 border-dashed ${theme === 'dark' ? 'border-zinc-700 hover:border-[#6B5DD3] text-zinc-400' : 'border-zinc-300 hover:border-[#6B5DD3] text-zinc-600'} hover:text-[#6B5DD3] transition-all flex items-center justify-center gap-2`}
              >
                <Plus className="w-5 h-5" />
                <span className="font-medium">Add New Category</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Category Modal */}
      {showAddCategory && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center sm:justify-center">
          <div 
            className="absolute inset-0" 
            onClick={() => setShowAddCategory(false)}
          />
          <div className={`relative w-full sm:max-w-md ${
            theme === 'dark' ? 'bg-zinc-900' : 'bg-white'
          } rounded-t-3xl sm:rounded-3xl shadow-xl`}>
            {/* Modal Header */}
            <div className={`flex items-center justify-between p-6 border-b ${
              theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'
            }`}>
              <h2 className={`text-xl font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
                Create Category
              </h2>
              <button
                onClick={() => setShowAddCategory(false)}
                className={`w-10 h-10 rounded-xl ${
                  theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-zinc-100 hover:bg-zinc-200'
                } flex items-center justify-center transition-colors`}
              >
                <X className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'}`} />
              </button>
            </div>

            {/* Form */}
            <div className="p-6 space-y-5">
              <div>
                <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-3`}>Select Icon</label>
                <div className="grid grid-cols-8 gap-2 max-h-48 overflow-y-auto p-1">
                  {availableIcons.map((iconName) => {
                    const IconComponent = getIcon(iconName);
                    return (
                      <button
                        key={iconName}
                        onClick={() => setNewCategoryEmoji(iconName)}
                        className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all ${
                          newCategoryEmoji === iconName
                            ? 'bg-[#6B5DD3] ring-2 ring-[#6B5DD3] ring-offset-2'
                            : `${theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-700' : 'bg-zinc-100 hover:bg-zinc-200'}`
                        }`}
                      >
                        <IconComponent className={`w-5 h-5 ${newCategoryEmoji === iconName ? 'text-white' : theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'}`} />
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className={`block text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-2`}>Category Name</label>
                <input
                  type="text"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="e.g., Subscriptions"
                  className={`w-full px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700 text-white placeholder:text-zinc-600' : 'border-zinc-200'} focus:border-[#6B5DD3] focus:outline-none focus:ring-2 focus:ring-[#6B5DD3]/20`}
                />
              </div>
            </div>

            {/* Footer */}
            <div className={`p-6 border-t ${theme === 'dark' ? 'border-zinc-800' : 'border-zinc-100'} flex gap-3`}>
              <button
                onClick={() => {
                  setShowAddCategory(false);
                  setNewCategoryName('');
                  setNewCategoryEmoji('Utensils');
                }}
                className={`flex-1 px-4 py-3 rounded-2xl border ${theme === 'dark' ? 'border-zinc-700 text-zinc-300 hover:bg-zinc-700' : 'border-zinc-200 text-zinc-700 hover:bg-zinc-50'} transition-colors`}
              >
                Cancel
              </button>
              <button
                onClick={handleAddNewCategory}
                className="flex-1 px-4 py-3 rounded-2xl bg-[#6B5DD3] text-white hover:bg-[#5B4DC3] transition-colors"
              >
                Add Category
              </button>
            </div>
          </div>
        </div>
      )}

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