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

## 8. Критерии приёмки

### Функциональные требования
| № | Критерий | Условие проверки | Ожидаемый результат |
|---|----------|------------------|---------------------|
| 1 | Кнопка "Получить отчёт" | Проверить UI-компонент | Присутствует кнопка для генерации отчёта |
| 2 | Вызов API /reports | Проверить код | Фронтенд вызывает эндпоинт Reports API |
| 3 | Отображение данных отчёта | Проверить UI-компонент | Данные отчёта отображаются пользователю |
| 4 | Скачивание отчёта | Проверить функциональность | Пользователь может скачать отчёт |
| 5 | Интеграция с Keycloak | Проверить аутентификацию | Фронтенд передаёт токен авторизации |

### Нефункциональные требования
| № | Критерий | Условие проверки | Ожидаемый результат |
|---|----------|------------------|---------------------|
| 1 | Обработка ошибки 401 | Проверить обработку ошибок | Показывается сообщение при неавторизованном доступе |
| 2 | Обработка ошибки 403 | Проверить обработку ошибок | Показывается сообщение при запрете доступа |
| 3 | Обработка ошибки 404 | Проверить обработку ошибок | Показывается сообщение при отсутствии отчёта |
| 4 | Обработка ошибки 500 | Проверить обработку ошибок | Показывается сообщение при серверной ошибке |
| 5 | Индикация загрузки | Проверить UI | Показывается индикатор загрузки при запросе |

### Граничные условия
| № | Критерий | Условие проверки | Ожидаемый результат |
|---|----------|------------------|---------------------|
| 1 | Нет аутентификации | Проверить поведение | Кнопка неактивна или перенаправляет на вход |
| 2 | Пользователь без протеза | Проверить поведение | Показывается соответствующее сообщение |
| 3 | Данные ещё не обработаны Airflow | Проверить обработку | Показывается сообщение о недоступности данных |

## 9. Тестовые спецификации

### 9.1 Юнит-тесты

#### 9.1.1 Тесты компонента ReportPage

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_render_report_button | Компонент ReportPage в DOM | Кнопка "Получить отчёт" отображается | Компонент смонтирован | Тест проходит если кнопка присутствует в DOM |
| 2 | test_button_disabled_without_auth | Пользователь не аутентифицирован (отсутствует токен) | Кнопка неактивна (disabled=true) | Токен отсутствует в localStorage/session | Тест проходит если кнопка заблокирована |
| 3 | test_button_enabled_with_auth | Пользователь аутентифицирован (есть токен) | Кнопка активна (disabled=false) | Токен присутствует | Тест проходит если кнопка активна |
| 4 | test_api_call_on_button_click | Клик по кнопке, пользователь аутентифицирован | Вызов API GET /api/v1/reports | Токен присутствует, обработчик клика вызван | Тест проходит если fetch/xhr вызван с правильным URL |

#### 9.1.2 Тесты отображения данных

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_display_report_data | API возвращает данные отчёта: {userId:123, totalSessions:45, avgSignalAmplitude:0.75, ...} | Данные отображаются в компоненте | Успешный ответ от API | Тест проходит если данные видны пользователю |
| 2 | test_display_error_401 | API возвращает 401 Unauthorized | Сообщение "Пожалуйста, войдите в систему" | Ответ 401 от API | Тест проходит если отображается сообщение об ошибке |
| 3 | test_display_error_403 | API возвращает 403 Forbidden | Сообщение "У вас нет доступа к этому отчёту" | Ответ 403 от API | Тест проходит если отображается сообщение об ошибке |
| 4 | test_display_error_404 | API возвращает 404 Not Found | Сообщение "Отчёт не найден" | Ответ 404 от API | Тест проходит если отображается сообщение |
| 5 | test_display_error_500 | API возвращает 500 Internal Server Error | Сообщение "Ошибка сервера" | Ответ 500 от API | Тест проходит если отображается сообщение |
| 6 | test_loading_indicator | Запрос в процессе (loading=true) | Индикатор загрузки отображается | Идёт запрос к API | Тест проходит если виден индикатор |
| 7 | test_hide_loading_on_complete | Запрос завершён (loading=false) | Индикатор загрузки скрыт | Запрос завершён | Тест проходит если индикатор скрыт |

#### 9.1.3 Тесты функциональности скачивания

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_download_report | Данные отчёта доступны, нажата кнопка "Скачать" | Файл скачивается (вызов window.download или создание blob) | Данные получены | Тест проходит если инициировано скачивание |
| 2 | test_download_button_disabled_without_data | Данные отчёта недоступны | Кнопка "Скачать" неактивна | Нет данных отчёта | Тест проходит если кнопка заблокирована |

### 9.2 Интеграционные тесты

| № | Название теста | Компоненты | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|------------|---------------------|-------------------|---------------------|
| 1 | test_frontend_api_integration | Frontend ↔ Reports API | Успешный вызов API и получение данных | Reports API запущен | Тест проходит если запрос успешен |
| 2 | test_frontend_passes_auth_token | Frontend ↔ Reports API | Токен передаётся в заголовке Authorization: Bearer {token} | Пользователь аутентифицирован | Тест проходит если токен в заголовке |
| 3 | test_frontend_handles_api_errors | Frontend ↔ Reports API | Корректная обработка ошибок (401, 403, 404, 500) | API возвращает ошибки | Тест проходит если ошибки обработаны |
| 4 | test_frontend_keycloak_integration | Frontend ↔ Keycloak | Токен получен от Keycloak | Keycloak запущен | Тест проходит если токен доступен |

### 9.3 E2E-тесты

| № | Название теста | Шаги | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|------|---------------------|-------------------|---------------------|
| 1 | test_e2e_user_gets_report | 1. Пользователь логинится в Keycloak<br>2. Переходит на страницу отчётов<br>3. Нажимает "Получить отчёт"<br>4. Видит свой отчёт | Отчёт отображается | Все сервисы запущены | Тест проходит если пользователь видит данные |
| 2 | test_e2e_user_cannot_see_other_report | 1. Пользователь A логинится<br>2. Запрашивает отчёт<br>3. Получает сообщение об ошибке | Сообщение "У вас нет доступа" | Все сервисы запущены | Тест проходит если показывается 403 |
| 3 | test_e2e_unauthenticated_cannot_access | 1. Пользователь не логинится<br>2. Переходит на страницу отчётов<br>3. Кнопка неактивна | Кнопка заблокирована или редирект на вход | Frontend запущен | Тест проходит если кнопка недоступна |
| 4 | test_e2e_report_after_etl_run | 1. Airflow запускает DAG<br>2. Данные в ClickHouse обновляются<br>3. Пользователь запрашивает отчёт | Данные актуальны | Airflow и API запущены | Тест проходит если данные свежие |
| 5 | test_e2e_user_downloads_report | 1. Пользователь получает отчёт<br>2. Нажимает "Скачать" | Файл скачивается на устройство | Все сервисы запущены | Тест проходит если скачивание инициировано |

### 9.4 Граничные условия

| № | Название теста | Шаги | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|------|---------------------|-------------------|---------------------|
| 1 | test_e2e_no_data_in_olap | 1. Airflow не запущен<br>2. Данных нет в ClickHouse<br>3. Пользователь запрашивает отчёт | Сообщение "Данные недоступны" | ClickHouse пустой | Тест проходит если показывается сообщение |
| 2 | test_e2e_user_without_prosthesis | 1. Пользователь без протеза в CRM<br>2. Запрашивает отчёт | Сообщение "Нет данных о протезе" | Пользователь без протеза | Тест проходит если показывается сообщение |
| 3 | test_e2e_concurrent_report_requests | 1. Множественные запросы отчёта<br>2. Отображаются данные | Корректные данные без гонок | Несколько параллельных запросов | Тест проходит если данные не дублируются |

### 9.5 Тестовые данные

#### 9.5.1 Данные отчёта для отображения

```typescript
// Тип ReportData
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
}

// Тестовые данные
const testReportData: ReportData = {
  userId: 123,
  reportDate: "2024-01-15",
  totalSessions: 45,
  avgSignalAmplitude: 0.75,
  maxSignalAmplitude: 1.2,
  minSignalAmplitude: 0.3,
  avgSignalFrequency: 150.5,
  totalUsageHours: 12.5,
  prosthesisType: "upper_limb",
  muscleGroup: "biceps"
};
```

#### 9.5.2 Сообщения об ошибках

| Код ошибки | Сообщение |
|------------|-----------|
| 401 | "Пожалуйста, войдите в систему" |
| 403 | "У вас нет доступа к этому отчёту" |
| 404 | "Отчёт не найден" |
| 500 | "Произошла ошибка на сервере" |
| Нет данных | "Данные недоступны. Пожалуйста, повторите позже." |

### 9.6 Среда тестирования

- **Запуск юнит-тестов:** `npm test` в директории frontend
- **Инструменты:** Jest, React Testing Library
- **Запуск E2E-тестов:** Playwright или Cypress
- **Команда E2E:** `npx playwright test` или `npx cypress run`
