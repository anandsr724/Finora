import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTheme } from '../contexts/ThemeContext';

interface DateTimePickerProps {
  value: Date;
  onChange: (date: Date) => void;
}

export default function DateTimePicker({ value, onChange }: DateTimePickerProps) {
  const { theme } = useTheme();
  const [mode, setMode] = useState<'date' | 'hour' | 'minute'>('date');
  const [tempDate, setTempDate] = useState(new Date(value));

  const hours = Array.from({ length: 24 }, (_, i) => i);
  const minutes = Array.from({ length: 60 }, (_, i) => i);

  const handleDateChange = (offset: number) => {
    const newDate = new Date(tempDate);
    newDate.setDate(newDate.getDate() + offset);
    setTempDate(newDate);
  };

  const handleHourClick = (hour: number) => {
    const newDate = new Date(tempDate);
    newDate.setHours(hour);
    setTempDate(newDate);
    setMode('minute');
  };

  const handleMinuteClick = (minute: number) => {
    const newDate = new Date(tempDate);
    newDate.setMinutes(minute);
    setTempDate(newDate);
    onChange(newDate);
    setMode('date');
  };

  const formatDateDisplay = (date: Date) => {
    return date.toLocaleDateString('en-IN', { 
      weekday: 'short', 
      month: 'short', 
      day: 'numeric',
      year: 'numeric'
    });
  };

  const formatTimeDisplay = (date: Date) => {
    return date.toLocaleTimeString('en-IN', { 
      hour: '2-digit', 
      minute: '2-digit',
      hour12: true 
    });
  };

  return (
    <div className={`w-full rounded-3xl p-6 ${theme === 'dark' ? 'bg-zinc-800 border-zinc-700' : 'bg-white border-zinc-100'} border`}>
      {mode === 'date' && (
        <div>
          <h3 className={`text-sm font-medium ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-4`}>Select Date</h3>
          
          {/* Date Navigation */}
          <div className="flex items-center justify-between mb-6">
            <button
              onClick={() => handleDateChange(-1)}
              className={`w-10 h-10 rounded-full flex items-center justify-center transition-colors ${theme === 'dark' ? 'hover:bg-zinc-700' : 'hover:bg-zinc-100'}`}
            >
              <ChevronLeft className="w-5 h-5" />
            </button>
            
            <div className={`text-lg font-semibold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
              {formatDateDisplay(tempDate)}
            </div>
            
            <button
              onClick={() => handleDateChange(1)}
              className={`w-10 h-10 rounded-full flex items-center justify-center transition-colors ${theme === 'dark' ? 'hover:bg-zinc-700' : 'hover:bg-zinc-100'}`}
            >
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>

          {/* Time Display */}
          <div className={`p-4 rounded-2xl mb-6 ${theme === 'dark' ? 'bg-zinc-900' : 'bg-zinc-100'} text-center`}>
            <p className={`text-sm ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-1`}>Time</p>
            <p className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
              {formatTimeDisplay(tempDate)}
            </p>
          </div>

          {/* Time Selection Button */}
          <button
            onClick={() => setMode('hour')}
            className="w-full px-4 py-3 rounded-2xl bg-gradient-to-r from-[#4F46E5] to-[#6C63FF] text-white font-medium hover:shadow-lg hover:shadow-indigo-500/30 transition-all"
          >
            Set Time
          </button>
        </div>
      )}

      {mode === 'hour' && (
        <div>
          <h3 className={`text-sm font-medium ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-4`}>Select Hour</h3>
          
          {/* Hour Grid */}
          <div className="grid grid-cols-6 gap-2 mb-6">
            {hours.map((hour) => (
              <button
                key={hour}
                onClick={() => handleHourClick(hour)}
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                  tempDate.getHours() === hour
                    ? 'bg-[#4F46E5] text-white ring-2 ring-[#4F46E5] scale-110'
                    : `${theme === 'dark' ? 'bg-zinc-700 hover:bg-zinc-600 text-zinc-300' : 'bg-zinc-100 hover:bg-zinc-200 text-zinc-700'}`
                }`}
              >
                {String(hour).padStart(2, '0')}
              </button>
            ))}
          </div>

          {/* Time Display */}
          <div className={`p-4 rounded-2xl mb-6 text-center ${theme === 'dark' ? 'bg-zinc-900' : 'bg-zinc-100'}`}>
            <p className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
              {formatTimeDisplay(tempDate)}
            </p>
          </div>

          <button
            onClick={() => setMode('minute')}
            className="w-full px-4 py-3 rounded-2xl bg-gradient-to-r from-[#4F46E5] to-[#6C63FF] text-white font-medium hover:shadow-lg hover:shadow-indigo-500/30 transition-all"
          >
            Next: Minutes
          </button>
        </div>
      )}

      {mode === 'minute' && (
        <div>
          <h3 className={`text-sm font-medium ${theme === 'dark' ? 'text-zinc-400' : 'text-zinc-600'} mb-4`}>Select Minutes</h3>
          
          <div className={`p-4 rounded-2xl mb-6 text-center ${theme === 'dark' ? 'bg-zinc-900' : 'bg-zinc-100'}`}>
            <p className={`text-2xl font-bold ${theme === 'dark' ? 'text-white' : 'text-zinc-900'}`}>
              {formatTimeDisplay(tempDate)}
            </p>
          </div>

          {/* Minute Grid */}
          <div className="grid grid-cols-6 gap-2 mb-6 max-h-48 overflow-y-auto">
            {minutes.map((minute) => (
              <button
                key={minute}
                onClick={() => handleMinuteClick(minute)}
                className={`px-2 py-2 rounded-lg text-xs font-medium transition-all ${
                  tempDate.getMinutes() === minute
                    ? 'bg-[#4F46E5] text-white ring-2 ring-[#4F46E5] scale-110'
                    : `${theme === 'dark' ? 'bg-zinc-700 hover:bg-zinc-600 text-zinc-300' : 'bg-zinc-100 hover:bg-zinc-200 text-zinc-700'}`
                }`}
              >
                {String(minute).padStart(2, '0')}
              </button>
            ))}
          </div>

          <button
            onClick={() => {
              onChange(tempDate);
              setMode('date');
            }}
            className="w-full px-4 py-3 rounded-2xl bg-gradient-to-r from-[#4F46E5] to-[#6C63FF] text-white font-medium hover:shadow-lg hover:shadow-indigo-500/30 transition-all"
          >
            Done
          </button>
        </div>
      )}
    </div>
  );
}
