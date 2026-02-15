import { Transaction } from '../App';
import { Upload, Camera } from 'lucide-react';
import { useTheme } from '../contexts/ThemeContext';

type HomeScreenProps = {
  transactions: Transaction[];
  onProcessImage: () => void;
  onEditTransaction: (transaction: Transaction) => void;
};

export default function HomeScreen({ transactions, onProcessImage, onEditTransaction }: HomeScreenProps) {
  const { theme } = useTheme();
  const recentTransactions = transactions.slice(0, 3);

  return (
    <div className="p-5 pb-20">
      {/* Header */}
      <div className="flex items-center gap-3 mb-8">
        <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#4F46E5] to-[#6C63FF] flex items-center justify-center">
          <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div>
          <h1 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Finora</h1>
        </div>
      </div>

      {/* Upload Section */}
      <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-6 shadow-sm border mb-6`}>
        <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Process Payment Screenshot</h2>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-6`}>
          Upload or share a UPI payment screenshot to automatically extract transaction details
        </p>
        <button
          onClick={onProcessImage}
          className="w-full bg-gradient-to-r from-[#4F46E5] to-[#6C63FF] text-white py-4 rounded-2xl flex items-center justify-center gap-3 shadow-lg shadow-indigo-500/30 hover:shadow-xl hover:shadow-indigo-500/40 transition-all active:scale-[0.98]"
        >
          <Upload className="w-5 h-5" />
          <span>Upload Screenshot</span>
        </button>
      </div>

      {/* Recent Transactions */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>Recent Transactions</h2>
          {transactions.length > 3 && (
            <button className="text-sm text-[#4F46E5]">
              View All
            </button>
          )}
        </div>

        {recentTransactions.length === 0 ? (
          <div className={`${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} rounded-3xl p-8 text-center border`}>
            <div className={`w-20 h-20 ${theme === 'dark' ? 'bg-zinc-700' : 'bg-zinc-100'} rounded-full flex items-center justify-center mx-auto mb-4`}>
              <Camera className={`w-10 h-10 ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'}`} />
            </div>
            <h3 className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>No transactions yet</h3>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Upload your first payment screenshot to get started
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {recentTransactions.map(transaction => (
              <button
                key={transaction.id}
                onClick={() => onEditTransaction(transaction)}
                className={`w-full ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700 hover:border-[#4F46E5]/40' : 'bg-white border-zinc-100 hover:border-[#4F46E5]/20'} rounded-2xl p-4 shadow-sm border hover:shadow-md transition-all active:scale-[0.98] text-left`}
              >
                <div className="flex items-start justify-between gap-3">
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
                      <p className={`${theme === 'dark' ? 'text-white' : 'text-zinc-900'} mb-1 truncate`}>{transaction.recipient}</p>
                      <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} truncate`}>{transaction.note}</p>
                      <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mt-1`}>
                        {transaction.date.toLocaleDateString('en-IN', { 
                          month: 'short', 
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit'
                        })}
                      </p>
                    </div>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <p className={theme === 'dark' ? 'text-white' : 'text-zinc-900'}>₹{transaction.amount.toLocaleString('en-IN')}</p>
                    <p className={`text-xs ${theme === 'dark' ? 'text-zinc-500' : 'text-zinc-400'} mt-1`}>{transaction.bankInfo}</p>
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Quick Stats */}
      {transactions.length > 0 && (
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-gradient-to-br from-emerald-500 to-teal-500 rounded-2xl p-4 text-white shadow-lg shadow-emerald-500/20">
            <p className="text-sm text-emerald-50 mb-1">This Month</p>
            <p className="text-2xl">
              ₹{transactions
                .filter(t => t.date.getMonth() === new Date().getMonth())
                .reduce((sum, t) => sum + t.amount, 0)
                .toLocaleString('en-IN')}
            </p>
          </div>
          <div className="bg-gradient-to-br from-amber-500 to-orange-500 rounded-2xl p-4 text-white shadow-lg shadow-amber-500/20">
            <p className="text-sm text-amber-50 mb-1">Transactions</p>
            <p className="text-2xl">{transactions.length}</p>
          </div>
        </div>
      )}

      {/* Floating Action Button */}
      <button
        onClick={onProcessImage}
        className="fixed bottom-24 right-8 w-14 h-14 bg-gradient-to-br from-[#4F46E5] to-[#6C63FF] rounded-full shadow-xl shadow-indigo-500/40 flex items-center justify-center hover:shadow-2xl hover:shadow-indigo-500/50 transition-all active:scale-[0.95]"
      >
        <Camera className="w-6 h-6 text-white" />
      </button>
    </div>
  );
}