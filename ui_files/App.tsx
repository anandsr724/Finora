import { useState } from 'react';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import HomeScreen from './components/HomeScreen';
import EditTransactionScreen from './components/EditTransactionScreen';
import TransactionHistoryScreen from './components/TransactionHistoryScreen';
import ReportsScreen from './components/ReportsScreen';
import SettingsScreen from './components/SettingsScreen';
import { Toaster } from './components/ui/sonner';

export type Transaction = {
  id: string;
  amount: number;
  recipient: string;
  note: string;
  date: Date;
  transactionId: string;
  bankInfo: string;
  category: string;
  emoji: string;
};

export type Category = {
  id: string;
  name: string;
  emoji: string;
  isCustom: boolean;
};

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
    { id: '1', name: 'Food & Dining', emoji: '🍕', isCustom: false },
    { id: '2', name: 'Shopping', emoji: '🛍️', isCustom: false },
    { id: '3', name: 'Transportation', emoji: '🚗', isCustom: false },
    { id: '4', name: 'Bills & Utilities', emoji: '💡', isCustom: false },
    { id: '5', name: 'Entertainment', emoji: '🎬', isCustom: false },
    { id: '6', name: 'Healthcare', emoji: '🏥', isCustom: false },
    { id: '7', name: 'Education', emoji: '📚', isCustom: false },
    { id: '8', name: 'Travel', emoji: '✈️', isCustom: false },
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
      emoji: '🍕',
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
      emoji: '🛍️',
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
      emoji: '🚗',
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
      emoji: '💡',
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
      emoji: '🎬',
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
    <div className={`min-h-screen ${theme === 'dark' ? 'bg-zinc-900' : 'bg-zinc-100'} flex items-center justify-center p-4`}>
      {/* Mobile Device Frame */}
      <div className={`relative w-full max-w-[400px] h-[844px] ${theme === 'dark' ? 'bg-zinc-950 border-zinc-800' : 'bg-white border-zinc-900'} rounded-[40px] shadow-2xl overflow-hidden border-8`}>
        {/* Status Bar */}
        <div className={`absolute top-0 left-0 right-0 h-11 ${theme === 'dark' ? 'bg-zinc-950' : 'bg-white'} z-50 flex items-center justify-between px-8`}>
          <span className={`text-sm ${theme === 'dark' ? 'text-white' : 'text-black'}`}>9:41</span>
          <div className="flex items-center gap-1">
            <div className={`w-4 h-3 border ${theme === 'dark' ? 'border-white' : 'border-black'} rounded-sm relative`}>
              <div className={`absolute right-0 top-1/2 -translate-y-1/2 w-0.5 h-1.5 ${theme === 'dark' ? 'bg-white' : 'bg-black'}`}></div>
            </div>
          </div>
        </div>

        {/* Screen Content */}
        <div className={`h-full pt-11 pb-2 ${theme === 'dark' ? 'bg-zinc-900' : 'bg-[#F8F9FB]'} overflow-hidden flex flex-col`}>
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
                        emoji: '🍕',
                      };
                      setEditingTransaction(newTransaction);
                    }}
                    onEditTransaction={handleEditTransaction}
                  />
                )}
                {currentScreen === 'history' && (
                  <TransactionHistoryScreen
                    transactions={transactions}
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
                    currentScreen === 'home' ? 'text-[#4F46E5]' : 'text-zinc-500'
                  }`}
                >
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                  </svg>
                  <span className="text-xs">Home</span>
                </button>
                <button
                  onClick={() => setCurrentScreen('history')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'history' ? 'text-[#4F46E5]' : 'text-zinc-500'
                  }`}
                >
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span className="text-xs">History</span>
                </button>
                <button
                  onClick={() => setCurrentScreen('reports')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'reports' ? 'text-[#4F46E5]' : 'text-zinc-500'
                  }`}
                >
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                  </svg>
                  <span className="text-xs">Reports</span>
                </button>
                <button
                  onClick={() => setCurrentScreen('settings')}
                  className={`flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors ${
                    currentScreen === 'settings' ? 'text-[#4F46E5]' : 'text-zinc-500'
                  }`}
                >
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
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