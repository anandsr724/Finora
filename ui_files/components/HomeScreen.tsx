import { Transaction } from '../App';
import { Upload, Camera, Plus, Edit3, TrendingUp, Wallet, Edit2, Trash2, X } from 'lucide-react';
import { useTheme } from '../contexts/ThemeContext';
import { getIcon } from '../utils/iconMapping';
import { useState } from 'react';
import { toast } from 'sonner';

type HomeScreenProps = {
  transactions: Transaction[];
  onProcessImage: () => void;
  onEditTransaction: (transaction: Transaction) => void;
  onAddManual: () => void;
  onDeleteTransaction: (id: string) => void;
};

export default function HomeScreen({ transactions, onProcessImage, onEditTransaction, onAddManual, onDeleteTransaction }: HomeScreenProps) {
  const { theme } = useTheme();
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const recentTransactions = transactions.slice(0, 3);

  const handleEdit = (transaction: Transaction) => {
    onEditTransaction(transaction);
    setSelectedTransaction(null);
  };

  const handleDelete = (id: string) => {
    onDeleteTransaction(id);
    setSelectedTransaction(null);
    toast.success('Transaction deleted');
  };

  return (
    <div className="p-6 pb-20">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-2">
          <h1 className={`text-3xl font-bold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>Finora</h1>
          <button className={`w-10 h-10 rounded-2xl ${theme === 'dark' ? 'bg-zinc-800' : 'bg-white'} shadow-sm flex items-center justify-center`}>
            <svg className={`w-5 h-5 ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
          </button>
        </div>
        <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
      </div>

      {/* Total Balance Card */}
      <div className={`${theme === 'dark' ? 'bg-gradient-to-br from-[#6B5DD3] to-[#8B7DE8]' : 'bg-gradient-to-br from-[#6B5DD3] to-[#8B7DE8]'} rounded-[28px] p-6 mb-6 shadow-lg shadow-purple-500/20`}>
        <p className="text-white/80 text-sm mb-2">Total Balance</p>
        <h2 className="text-white text-4xl font-bold mb-6">
          ₹{transactions.reduce((sum, t) => sum + t.amount, 0).toLocaleString('en-IN')}
        </h2>
        <div className="grid grid-cols-2 gap-4">
          <button
            onClick={onProcessImage}
            className="bg-white/20 backdrop-blur-sm text-white py-3 px-4 rounded-2xl flex items-center justify-center gap-2 hover:bg-white/30 transition-all active:scale-95"
          >
            <Upload className="w-4 h-4" />
            <span className="text-sm font-medium">Upload</span>
          </button>
          <button
            onClick={onAddManual}
            className="bg-white text-[#6B5DD3] py-3 px-4 rounded-2xl flex items-center justify-center gap-2 hover:bg-white/90 transition-all active:scale-95 font-medium"
          >
            <Plus className="w-4 h-4" />
            <span className="text-sm">Add Manual</span>
          </button>
        </div>
      </div>

      {/* Quick Stats */}
      {transactions.length > 0 && (
        <div className="grid grid-cols-2 gap-4 mb-6">
          <div className={`${theme === 'dark' ? 'bg-zinc-800' : 'bg-white'} rounded-3xl p-5 shadow-sm`}>
            <div className="flex items-center gap-2 mb-3">
              <div className="w-10 h-10 rounded-2xl bg-emerald-100 flex items-center justify-center">
                <TrendingUp className="w-5 h-5 text-emerald-600" />
              </div>
            </div>
            <p className={`text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-1`}>This Month</p>
            <p className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>
              ₹{transactions
                .filter(t => t.date.getMonth() === new Date().getMonth())
                .reduce((sum, t) => sum + t.amount, 0)
                .toLocaleString('en-IN')}
            </p>
          </div>
          <div className={`${theme === 'dark' ? 'bg-zinc-800' : 'bg-white'} rounded-3xl p-5 shadow-sm`}>
            <div className="flex items-center gap-2 mb-3">
              <div className="w-10 h-10 rounded-2xl bg-purple-100 flex items-center justify-center">
                <Wallet className="w-5 h-5 text-[#6B5DD3]" />
              </div>
            </div>
            <p className={`text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'} mb-1`}>Transactions</p>
            <p className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>{transactions.length}</p>
          </div>
        </div>
      )}

      {/* Recent Transactions */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-lg font-semibold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>Recent Activity</h3>
          {transactions.length > 3 && (
            <button className="text-sm text-[#6B5DD3] font-medium">See All</button>
          )}
        </div>

        {recentTransactions.length === 0 ? (
          <div className={`${theme === 'dark' ? 'bg-zinc-800' : 'bg-white'} rounded-3xl p-8 text-center shadow-sm`}>
            <div className={`w-20 h-20 ${theme === 'dark' ? 'bg-zinc-700' : 'bg-gray-50'} rounded-full flex items-center justify-center mx-auto mb-4`}>
              <Camera className={`w-10 h-10 ${theme === 'dark' ? 'text-zinc-500' : 'text-gray-400'}`} />
            </div>
            <h3 className={`text-base font-medium mb-2 ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>No transactions yet</h3>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Add your first transaction to get started
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {recentTransactions.map(transaction => {
              const IconComponent = getIcon(transaction.emoji);
              return (
                <button
                  key={transaction.id}
                  onClick={() => setSelectedTransaction(transaction)}
                  className={`w-full ${theme === 'dark' ? 'bg-zinc-800 hover:bg-zinc-750' : 'bg-white hover:bg-gray-50'} rounded-3xl p-4 shadow-sm transition-all active:scale-[0.98] text-left`}
                >
                  <div className="flex items-center gap-4">
                    <div className={`w-14 h-14 rounded-2xl ${theme === 'dark' ? 'bg-zinc-700' : 'bg-gradient-to-br from-purple-50 to-indigo-50'} flex items-center justify-center flex-shrink-0`}>
                      <IconComponent className="w-7 h-7 text-[#6B5DD3]" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className={`font-medium mb-1 ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>{transaction.recipient}</p>
                      <div className="flex items-center gap-2">
                        <p className={`text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>{transaction.category}</p>
                        <span className={`text-xs ${theme === 'dark' ? 'text-zinc-600' : 'text-zinc-300'}`}>•</span>
                        <p className={`text-xs ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-500'}`}>
                          {transaction.date.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })}
                        </p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className={`font-semibold ${theme === 'dark' ? 'text-white' : 'text-[#2D3142]'}`}>₹{transaction.amount.toLocaleString('en-IN')}</p>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Floating Action Button */}
      <button
        onClick={onAddManual}
        className="fixed bottom-24 right-8 w-16 h-16 bg-gradient-to-br from-[#6B5DD3] to-[#8B7DE8] rounded-full shadow-xl shadow-purple-500/30 flex items-center justify-center hover:shadow-2xl hover:scale-105 transition-all active:scale-95"
      >
        <Plus className="w-7 h-7 text-white" strokeWidth={2.5} />
      </button>

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
    </div>
  );
}