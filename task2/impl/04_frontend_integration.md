# План интеграции Frontend с Reports API

## 1. Обзор

Интеграция React-приложения с API сервиса отчётов для получения и отображения данных о работе протезов.

### 1.1 Текущее состояние

В проекте уже существует компонент [`ReportPage.tsx`](app/frontend/src/components/ReportPage.tsx), который:
- Использует Keycloak для аутентификации
- Имеет кнопку "Download Report"
- Делает запрос к `${process.env.REACT_APP_API_URL}/reports`

### 1.2 Необходимые изменения

1. Настройка переменных окружения
2. Улучшение обработки ответа API
3. Отображение данных отчёта пользователю
4. Обработка ошибок

---

## 2. Настройка окружения

### 2.1 Изменение .env файла

```env
# API URLs
REACT_APP_API_URL=http://localhost:8081/api/v1
REACT_APP_AUTH_URL=http://localhost:8000

# Keycloak
REACT_APP_KEYCLOAK_URL=http://localhost:8080
REACT_APP_KEYCLOAK_REALM=reports-realm
REACT_APP_KEYCLOAK_CLIENT_ID=bionicpro-auth
```

---

## 3. Улучшение компонента ReportPage

### 3.1 Обновлённый код ReportPage.tsx

```tsx
import React, { useState, useEffect } from 'react';
import { useKeycloak } from '@react-keycloak/web';

interface ReportData {
  userId: number;
  reportDate: string;
  totalSessions: number;
  avgSignalAmplitude: number;
  maxSignalAmplitude: number;
  minSignalAmplitude: number;
  avgSignalFrequency: number;
  totalUsageHours: number;
  prosthesisType: string;
  muscleGroup: string;
  customerInfo: {
    name: string;
    email: string;
    age: number;
    gender: string;
    country: string;
  };
}

const ReportPage: React.FC = () => {
  const { keycloak, initialized } = useKeycloak();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reportData, setReportData] = useState<ReportData | null>(null);

  const fetchReport = async () => {
    if (!keycloak?.token) {
      setError('Not authenticated');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const response = await fetch(
        `${process.env.REACT_APP_API_URL}/reports`,
        {
          headers: {
            'Authorization': `Bearer ${keycloak.token}`
          }
        }
      );

      if (!response.ok) {
        if (response.status === 403) {
          throw new Error('You can only access your own report');
        }
        if (response.status === 401) {
          throw new Error('Authentication required');
        }
        throw new Error(`Error: ${response.status}`);
      }

      const data = await response.json();
      setReportData(data);
      
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  const downloadReport = () => {
    if (!reportData) return;
    
    // Создание JSON файла для скачивания
    const blob = new Blob([JSON.stringify(reportData, null, 2)], {
      type: 'application/json'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `report-${reportData.userId}-${reportData.reportDate}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  // Автоматическая загрузка данных при монтировании
  useEffect(() => {
    if (initialized && keycloak.authenticated) {
      fetchReport();
    }
  }, [initialized, keycloak.authenticated]);

  if (!initialized) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-xl">Loading...</div>
      </div>
    );
  }

  if (!keycloak.authenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <div className="p-8 bg-white rounded-lg shadow-md text-center">
          <h1 className="text-2xl font-bold mb-4">Authentication Required</h1>
          <p className="mb-6">Please login to view your reports</p>
          <button
            onClick={() => keycloak.login()}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100 p-4">
      <div className="w-full max-w-2xl p-8 bg-white rounded-lg shadow-md">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold">Usage Reports</h1>
          <button
            onClick={() => keycloak.logout()}
            className="px-3 py-1 text-sm text-gray-600 hover:text-gray-800"
          >
            Logout
          </button>
        </div>
        
        <div className="mb-6">
          <button
            onClick={fetchReport}
            disabled={loading}
            className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${
              loading ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            {loading ? 'Loading...' : 'Refresh Report'}
          </button>

          {reportData && (
            <button
              onClick={downloadReport}
              className="ml-4 px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600"
            >
              Download JSON
            </button>
          )}
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-100 text-red-700 rounded">
            Error: {error}
          </div>
        )}

        {reportData && (
          <div className="space-y-6">
            {/* User Info Section */}
            <div className="border-b pb-4">
              <h2 className="text-lg font-semibold mb-3">User Information</h2>
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-600">Name:</span>
                  <span className="ml-2 font-medium">{reportData.customerInfo.name}</span>
                </div>
                <div>
                  <span className="text-gray-600">Email:</span>
                  <span className="ml-2 font-medium">{reportData.customerInfo.email}</span>
                </div>
                <div>
                  <span className="text-gray-600">Age:</span>
                  <span className="ml-2 font-medium">{reportData.customerInfo.age}</span>
                </div>
                <div>
                  <span className="text-gray-600">Gender:</span>
                  <span className="ml-2 font-medium">{reportData.customerInfo.gender}</span>
                </div>
                <div>
                  <span className="text-gray-600">Country:</span>
                  <span className="ml-2 font-medium">{reportData.customerInfo.country}</span>
                </div>
              </div>
            </div>

            {/* Prosthesis Info Section */}
            <div className="border-b pb-4">
              <h2 className="text-lg font-semibold mb-3">Prosthesis Information</h2>
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-600">Prosthesis Type:</span>
                  <span className="ml-2 font-medium">{reportData.prosthesisType}</span>
                </div>
                <div>
                  <span className="text-gray-600">Muscle Group:</span>
                  <span className="ml-2 font-medium">{reportData.muscleGroup}</span>
                </div>
              </div>
            </div>

            {/* Statistics Section */}
            <div>
              <h2 className="text-lg font-semibold mb-3">Usage Statistics</h2>
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-600">Total Sessions:</span>
                  <span className="ml-2 font-medium">{reportData.totalSessions}</span>
                </div>
                <div>
                  <span className="text-gray-600">Total Usage Hours:</span>
                  <span className="ml-2 font-medium">
                    {reportData.totalUsageHours.toFixed(2)}h
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Avg Signal Amplitude:</span>
                  <span className="ml-2 font-medium">
                    {reportData.avgSignalAmplitude.toFixed(3)}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Max Signal Amplitude:</span>
                  <span className="ml-2 font-medium">
                    {reportData.maxSignalAmplitude.toFixed(3)}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Min Signal Amplitude:</span>
                  <span className="ml-2 font-medium">
                    {reportData.minSignalAmplitude.toFixed(3)}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Avg Signal Frequency:</span>
                  <span className="ml-2 font-medium">
                    {reportData.avgSignalFrequency.toFixed(2)} Hz
                  </span>
                </div>
                <div className="col-span-2">
                  <span className="text-gray-600">Report Date:</span>
                  <span className="ml-2 font-medium">{reportData.reportDate}</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {!reportData && !error && !loading && (
          <div className="text-center text-gray-500 py-8">
            No report data available. Click "Refresh Report" to load your data.
          </div>
        )}
      </div>
    </div>
  );
};

export default ReportPage;
```

---

## 4. Обновление App.tsx

### 4.1 Добавление маршрута для ReportPage

```tsx
import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import ReportPage from './components/ReportPage';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/reports" element={<ReportPage />} />
        {/* Другие маршруты */}
      </Routes>
    </Router>
  );
}

export default App;
```

---

## 5. Обработка ошибок

### 5.1 Типы ошибок и их обработка

| Код ошибки | Сообщение | Действие пользователя |
|------------|-----------|----------------------|
| 401 | Unauthorized | Перенаправление на login |
| 403 | Forbidden | Показать сообщение о недоступе |
| 404 | Not Found | Показать "Нет данных" |
| 500 | Server Error | Показать сообщение об ошибке |

---

## 6. Требования к безопасности

### 6.1 Проверки

- [x] Пользователь должен быть аутентифицирован
- [x] Токен передаётся в заголовке Authorization
- [x] Запрещено получение отчётов других пользователей (проверка на сервере)
- [x] Обработка истечения токена

---

## 7. Следующие шаги

Перейдите к документу [05_deployment_checklist.md](05_deployment_checklist.md) для итогового чек-листа развёртывания.
