import { useState, useEffect } from 'react';
import './styles/globals.css';
import HomeScreen from './components/HomeScreen';
import EditTransactionScreen from './components/EditTransactionScreen';
import TransactionHistoryScreen from './components/TransactionHistoryScreen';
import ReportsScreen from './components/ReportsScreen';
import SettingsScreen from './components/SettingsScreen';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import { Home, Clock, BarChart3, Settings } from 'lucide-react';
import { Toaster } from 'sonner';

export interface Transaction {
  id: string;
  amount: number;
  recipient: string;
  note: string;
  date: Date;
  transactionId: string;
  bankInfo: string;
  category: string;
  emoji: string;
}

export interface Category {
  id: string;
  name: string;
  icon: string;
  isCustom: boolean;
}

export default function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
}

function AppContent() {
  const { theme } = useTheme();
  const [currentScreen, setCurrentScreen] = useState<'home' | 'history' | 'reports' | 'settings'>('home');
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  
  const [categories, setCategories] = useState<Category[]>([
    { id: '1', name: 'Food & Dining', icon: 'Utensils', isCustom: false },
    { id: '2', name: 'Shopping', icon: 'ShoppingBag', isCustom: false },
    { id: '3', name: 'Transportation', icon: 'Car', isCustom: false },
    { id: '4', name: 'Bills & Utilities', icon: 'Zap', isCustom: false },
    { id: '5', name: 'Entertainment', icon: 'Film', isCustom: false },
    { id: '6', name: 'Healthcare', icon: 'Heart', isCustom: false },
    { id: '7', name: 'Education', icon: 'GraduationCap', isCustom: false },
    { id: '8', name: 'Travel', icon: 'Plane', isCustom: false },
  ]);

  const [transactions, setTransactions] = useState<Transaction[]>([
    {
      id: '1',
      amount: 450,
      recipient: 'Swiggy',
      note: 'Dinner order',
      date: new Date('2025-10-11T20:30:00'),
      transactionId: 'UPI/431256789',
      bankInfo: 'Google Pay',
      category: 'Food & Dining',
      emoji: 'Utensils',
    },
    {
      id: '2',
      amount: 1299,
      recipient: 'Amazon India',
      note: 'Wireless earbuds',
      date: new Date('2025-10-10T15:20:00'),
      transactionId: 'UPI/431256790',
      bankInfo: 'PhonePe',
      category: 'Shopping',
      emoji: 'ShoppingBag',
    },
    {
      id: '3',
      amount: 180,
      recipient: 'Uber India',
      note: 'Ride to office',
      date: new Date('2025-10-10T09:15:00'),
      transactionId: 'UPI/431256791',
      bankInfo: 'ICICI Bank',
      category: 'Transportation',
      emoji: 'Car',
    },
    {
      id: '4',
      amount: 2500,
      recipient: 'Adani Electricity',
      note: 'Monthly electricity bill',
      date: new Date('2025-10-09T12:00:00'),
      transactionId: 'UPI/431256792',
      bankInfo: 'Google Pay',
      category: 'Bills & Utilities',
      emoji: 'Zap',
    },
    {
      id: '5',
      amount: 799,
      recipient: 'BookMyShow',
      note: 'Movie tickets - 2x',
      date: new Date('2025-10-08T19:45:00'),
      transactionId: 'UPI/431256793',
      bankInfo: 'PhonePe',
      category: 'Entertainment',
      emoji: 'Film',
    },
  ]);

  const handleSaveTransaction = (transaction: Transaction) => {
    if (editingTransaction) {
      setTransactions(prev => prev.map(t => t.id === transaction.id ? transaction : t));
    } else {
      setTransactions(prev => [transaction, ...prev]);
    }
    setEditingTransaction(null);
  };

  const handleDeleteTransaction = (id: string) => {
    setTransactions(prev => prev.filter(t => t.id !== id));
  };

  const handleEditTransaction = (transaction: Transaction) => {
    setEditingTransaction(transaction);
  };

  const handleAddCategory = (category: Category) => {
    setCategories(prev => [...prev, category]);
  };

  const handleDeleteCategory = (id: string) => {
    setCategories(prev => prev.filter(c => c.id !== id));
  };

  return (
    <div className={`min-h-screen ${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F5F5F7]'} flex items-center justify-center p-4`}>
      {/* Mobile Device Frame */}
      <div className={`relative w-full max-w-[400px] h-[844px] ${theme === 'dark' ? 'bg-zinc-950 border-zinc-800' : 'bg-[#F5F5F7] border-zinc-200'} rounded-[40px] shadow-2xl overflow-hidden border-8`}>
        {/* Status Bar */}
        <div className={`absolute top-0 left-0 right-0 h-11 ${theme === 'dark' ? 'bg-zinc-950' : 'bg-[#F5F5F7]'} z-50 flex items-center justify-between px-8`}>
          <span className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-black'}`}>9:41</span>
          <div className="flex items-center gap-1">
            <div className={`w-4 h-3 border ${theme === 'dark' ? 'border-white' : 'border-black'} rounded-sm relative`}>
              <div className={`absolute right-0 top-1/2 -translate-y-1/2 w-0.5 h-1.5 ${theme === 'dark' ? 'bg-white' : 'bg-black'}`}></div>
            </div>
          </div>
        </div>

        {/* Screen Content */}
        <div className={`h-full pt-11 pb-2 ${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F5F5F7]'} overflow-hidden flex flex-col`}>
          {editingTransaction ? (
            <EditTransactionScreen
              transaction={editingTransaction}
              categories={categories}
              onSave={handleSaveTransaction}
              onCancel={() => setEditingTransaction(null)}
              onAddCategory={handleAddCategory}
            />
          ) : (
            <>
              <div className="flex-1 overflow-auto">
                {currentScreen === 'home' && (
                  <HomeScreen
                    transactions={transactions}
                    onProcessImage={() => {
                      // Simulate processing a new image
                      const newTransaction: Transaction = {
                        id: Date.now().toString(),
                        amount: 0,
                        recipient: '',
                        note: '',
                        date: new Date(),
                        transactionId: '',
                        bankInfo: 'Google Pay',
                        category: 'Food & Dining',
                        emoji: 'Utensils',
                      };
                      setEditingTransaction(newTransaction);
                    }}
                    onAddManual={() => {
                      // Create a blank transaction for manual entry
                      const newTransaction: Transaction = {
                        id: Date.now().toString(),
                        amount: 0,
                        recipient: '',
                        note: '',
                        date: new Date(),
                        transactionId: '',
                        bankInfo: 'Google Pay',
                        category: 'Food & Dining',
                        emoji: 'Utensils',
                      };
                      setEditingTransaction(newTransaction);
                    }}
                    onEditTransaction={handleEditTransaction}
                    onDeleteTransaction={handleDeleteTransaction}
                  />
                )}
                {currentScreen === 'history' && (
                  <TransactionHistoryScreen
                    transactions={transactions}
                    categories={categories}
                    onEdit={handleEditTransaction}
                    onDelete={handleDeleteTransaction}
                  />
                )}
                {currentScreen === 'reports' && (
                  <ReportsScreen transactions={transactions} />
                )}
                {currentScreen === 'settings' && (
                  <SettingsScreen
                    categories={categories}
                    onAddCategory={handleAddCategory}
                    onDeleteCategory={handleDeleteCategory}
                  />
                )}
              </div>

              {/* Bottom Navigation */}
              <div className={`${theme === 'dark' ? 'bg-zinc-950 border-zinc-800' : 'bg-white border-zinc-200'} border-t px-4 py-3 flex items-center justify-around`}>
                <button
                  onClick={() => setCurrentScreen('home')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'home' ? 'text-[#6B5DD3]' : 'text-zinc-400'
                  }`}
                >
                  <Home className="w-6 h-6" />
                  <span className="text-xs">Home</span>
                </button>
                <button
                  onClick={() => setCurrentScreen('history')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'history' ? 'text-[#6B5DD3]' : 'text-zinc-400'
                  }`}
                >
                  <Clock className="w-6 h-6" />
                  <span className="text-xs">History</span>
                </button>
                <button
                  onClick={() => setCurrentScreen('reports')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'reports' ? 'text-[#6B5DD3]' : 'text-zinc-400'
                  }`}
                >
                  <BarChart3 className="w-6 h-6" />
                  <span className="text-xs">Reports</span>
                </button>
                <button
                  onClick={() => setCurrentScreen('settings')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'settings' ? 'text-[#6B5DD3]' : 'text-zinc-400'
                  }`}
                >
                  <Settings className="w-6 h-6" />
                  <span className="text-xs">Settings</span>
                </button>
              </div>
            </>
          )}
        </div>
      </div>
      <Toaster />
    </div>
  );
}