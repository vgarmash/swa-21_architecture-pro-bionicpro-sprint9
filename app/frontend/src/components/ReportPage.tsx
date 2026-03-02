import React, { useState, useEffect, useCallback } from 'react';

interface UserInfo {
  name: string;
  email: string;
  age: number;
  gender: string;
  country: string;
}

interface ProsthesisInfo {
  type: string;
  muscleGroup: string;
}

interface Statistics {
  totalSessions: number;
  totalHours: number;
  averageAmplitude: number;
  averageFrequency: number;
}

interface ReportData {
  user: UserInfo;
  prosthesis: ProsthesisInfo;
  statistics: Statistics;
}

interface AuthStatus {
  authenticated: boolean;
  userId?: string;
  username?: string;
  roles?: string[];
  sessionExpiresAt?: string;
}

const ReportPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [reportData, setReportData] = useState<ReportData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);

  // BFF API base URL - указывает на сервис bionicpro-auth
  const apiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8000';

  // Проверка статуса аутентификации через BFF
  const checkAuthStatus = useCallback(async () => {
    try {
      // Используем /api/auth/status (BFF эндпоинт)
      const response = await fetch(`${apiUrl}/api/auth/status`, {
        credentials: 'include', // Важно: отправлять куки сессии
      });

      if (response.ok) {
        const authStatus: AuthStatus = await response.json();
        setIsAuthenticated(authStatus.authenticated);
        return authStatus.authenticated;
      } else if (response.status === 401) {
        setIsAuthenticated(false);
        return false;
      } else {
        setError('Failed to check authentication status');
        return false;
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Network error');
      return false;
    }
  }, [apiUrl]);

  // Получение данных отчета через BFF
  const fetchReport = async (showLoading = true) => {
    try {
      if (showLoading) setLoading(true);
      setError(null);

      // Используем /api/reports (BFF эндпоинт - нужен прокси к bionicpro-reports)
      const response = await fetch(`${apiUrl}/api/reports`, {
        credentials: 'include', // Важно: отправлять куки сессии
      });

      if (!response.ok) {
        if (response.status === 401) {
          setIsAuthenticated(false);
          setError('Unauthorized. Please login again.');
        } else if (response.status === 403) {
          setError('Access forbidden. You do not have permission to view this report.');
        } else if (response.status === 404) {
          setError('Report not found.');
        } else if (response.status === 500) {
          setError('Server error. Please try again later.');
        } else {
          setError(`Error: ${response.status} ${response.statusText}`);
        }
        return;
      }

      const data = await response.json();
      setReportData(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  // Обработка входа - перенаправление на BFF login
  const handleLogin = () => {
    window.location.href = `${apiUrl}/api/auth/login`;
  };

  // Обработка выхода
  const handleLogout = async () => {
    try {
      await fetch(`${apiUrl}/api/auth/logout`, {
        method: 'POST',
        credentials: 'include',
      });
      setIsAuthenticated(false);
      setReportData(null);
    } catch (err) {
      console.error('Logout error:', err);
    }
  };

  // Проверка статуса аутентификации при монтировании
  useEffect(() => {
    const initAuth = async () => {
      const authenticated = await checkAuthStatus();
      if (authenticated) {
        fetchReport(false);
      }
    };
    initAuth();
  }, [checkAuthStatus]);

  // Показывать загрузку пока проверяется аутентификация
  if (isAuthenticated === null) {
    return <div className="flex items-center justify-center min-h-screen bg-gray-100">Loading...</div>;
  }

  // Показывать кнопку входа если не аутентифицирован
  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <button
          onClick={handleLogin}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Login
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100 p-4">
      <div className="p-8 bg-white rounded-lg shadow-md w-full max-w-2xl">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold">Отчёты об использовании</h1>
          <button
            onClick={handleLogout}
            className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 text-sm"
          >
            Выйти
          </button>
        </div>

        <button
          onClick={() => fetchReport(true)}
          disabled={loading}
          className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 mb-6 ${
            loading ? 'opacity-50 cursor-not-allowed' : ''
          }`}
        >
          {loading ? 'Генерация отчёта...' : 'Скачать отчёт (JSON)'}
        </button>

        {error && (
          <div className="mb-6 p-4 bg-red-100 text-red-700 rounded">
            {error}
          </div>
        )}

        {reportData && (
          <div className="space-y-6">
            {/* Информация о пользователе */}
            <div className="border rounded-lg p-4">
              <h2 className="text-xl font-semibold mb-3">Информация о пользователе</h2>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="font-medium">Имя:</span> {reportData.user.name}
                </div>
                <div>
                  <span className="font-medium">Email:</span> {reportData.user.email}
                </div>
                <div>
                  <span className="font-medium">Возраст:</span> {reportData.user.age}
                </div>
                <div>
                  <span className="font-medium">Пол:</span> {reportData.user.gender}
                </div>
                <div>
                  <span className="font-medium">Страна:</span> {reportData.user.country}
                </div>
              </div>
            </div>

            {/* Информация о протезе */}
            <div className="border rounded-lg p-4">
              <h2 className="text-xl font-semibold mb-3">Информация о протезе</h2>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="font-medium">Тип:</span> {reportData.prosthesis.type}
                </div>
                <div>
                  <span className="font-medium">Группа мышц:</span> {reportData.prosthesis.muscleGroup}
                </div>
              </div>
            </div>

            {/* Статистика использования */}
            <div className="border rounded-lg p-4">
              <h2 className="text-xl font-semibold mb-3">Статистика использования</h2>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="font-medium">Всего сессий:</span> {reportData.statistics.totalSessions}
                </div>
                <div>
                  <span className="font-medium">Всего часов:</span> {reportData.statistics.totalHours}
                </div>
                <div>
                  <span className="font-medium">Средняя амплитуда:</span> {reportData.statistics.averageAmplitude}
                </div>
                <div>
                  <span className="font-medium">Средняя частота:</span> {reportData.statistics.averageFrequency}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ReportPage;
