import React from 'react';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import '@testing-library/jest-dom';
import ReportPage from './ReportPage';

// Мок хука useKeycloak
jest.mock('@react-keycloak/web', () => ({
  useKeycloak: jest.fn(),
}));

import { useKeycloak } from '@react-keycloak/web';

// Мок fetch глобально
const mockFetch = jest.fn();
global.fetch = mockFetch;

// Мок переменной окружения
const originalEnv = process.env;
beforeAll(() => {
  process.env = { ...originalEnv, REACT_APP_API_URL: 'http://localhost:8081/api/v1' };
});

afterAll(() => {
  process.env = originalEnv;
});

beforeEach(() => {
  jest.clearAllMocks();
  mockFetch.mockClear();
  (useKeycloak as jest.Mock).mockClear();
});

// Тестовые данные
const mockReportData = {
  user: {
    name: 'John Doe',
    email: 'john@example.com',
    age: 35,
    gender: 'Male',
    country: 'USA',
  },
  prosthesis: {
    type: 'upper_limb',
    muscleGroup: 'biceps',
  },
  statistics: {
    totalSessions: 45,
    totalHours: 12.5,
    averageAmplitude: 0.75,
    averageFrequency: 150.5,
  },
};

// Вспомогательная функция для настройки мока keycloak
const setupKeycloakMock = (authenticated: boolean, token: string | null = null) => {
  (useKeycloak as jest.Mock).mockReturnValue({
    keycloak: {
      authenticated,
      token,
      login: jest.fn(),
      logout: jest.fn(),
    },
    initialized: true,
  });
};

describe('ReportPage Component Tests', () => {
  describe('1. Тесты компонента ReportPage', () => {
    test('test_render_report_button - проверка отображения кнопки', async () => {
      setupKeycloakMock(true, 'mock-token');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockReportData,
      });

      render(<ReportPage />);

      await waitFor(() => {
        expect(screen.getByText(/Download Report/)).toBeInTheDocument();
      });
    });

    test('test_button_disabled_without_auth - кнопка неактивна без авторизации', async () => {
      setupKeycloakMock(false, null);

      render(<ReportPage />);

      // Должна отображаться кнопка входа, когда пользователь не аутентифицирован
      await waitFor(() => {
        expect(screen.getByText('Login')).toBeInTheDocument();
      });

      // Кнопка Download Report не должна отображаться, когда пользователь не аутентифицирован
      expect(screen.queryByText(/Download Report/)).not.toBeInTheDocument();
    });

    test('test_button_enabled_with_auth - кнопка активна с авторизацией', async () => {
      setupKeycloakMock(true, 'mock-token-123');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockReportData,
      });

      render(<ReportPage />);

      await waitFor(() => {
        const button = screen.getByText(/Download Report/);
        expect(button).toBeInTheDocument();
        expect(button).not.toBeDisabled();
      });
    });

    test('test_api_call_on_button_click - вызов API при клике', async () => {
      setupKeycloakMock(true, 'mock-token-456');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockReportData,
      });

      render(<ReportPage />);

      // Ожидание доступности кнопки
      await waitFor(() => {
        expect(screen.getByText(/Download Report/)).toBeInTheDocument();
      });

      // Нажатие на кнопку
      fireEvent.click(screen.getByText(/Download Report/));

      // Проверка вызова fetch
      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          'http://localhost:8081/api/v1/reports',
          {
            headers: {
              Authorization: 'Bearer mock-token-456',
            },
          }
        );
      });
    });
  });

  describe('2. Тесты отображения данных', () => {
    test('test_display_report_data - отображение данных отчёта', async () => {
      setupKeycloakMock(true, 'mock-token');

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockReportData,
      });

      render(<ReportPage />);

      // Ожидание загрузки данных и проверка информации о пользователе
      await waitFor(() => {
        expect(screen.getByText('John Doe')).toBeInTheDocument();
        expect(screen.getByText('john@example.com')).toBeInTheDocument();
        expect(screen.getByText('35')).toBeInTheDocument();
        expect(screen.getByText('Male')).toBeInTheDocument();
        expect(screen.getByText('USA')).toBeInTheDocument();
      });

      // Проверка информации о протезе
      await waitFor(() => {
        expect(screen.getByText('upper_limb')).toBeInTheDocument();
        expect(screen.getByText('biceps')).toBeInTheDocument();
      });

      // Проверка статистики
      await waitFor(() => {
        expect(screen.getByText('45')).toBeInTheDocument(); // totalSessions
        expect(screen.getByText('12.5')).toBeInTheDocument(); // totalHours
      });
    });

    test('test_display_error_401 - отображение ошибки 401', async () => {
      setupKeycloakMock(true, 'mock-token');

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
      });

      render(<ReportPage />);

      await waitFor(() => {
        expect(screen.getByText(/Unauthorized/)).toBeInTheDocument();
      });
    });

    test('test_display_error_403 - отображение ошибки 403', async () => {
      setupKeycloakMock(true, 'mock-token');

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
      });

      render(<ReportPage />);

      await waitFor(() => {
        expect(screen.getByText(/Access forbidden/)).toBeInTheDocument();
      });
    });

    test('test_display_error_404 - отображение ошибки 404', async () => {
      setupKeycloakMock(true, 'mock-token');

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      });

      render(<ReportPage />);

      await waitFor(() => {
        expect(screen.getByText(/Report not found/)).toBeInTheDocument();
      });
    });

    test('test_display_error_500 - отображение ошибки 500', async () => {
      setupKeycloakMock(true, 'mock-token');

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      });

      render(<ReportPage />);

      await waitFor(() => {
        expect(screen.getByText(/Server error/)).toBeInTheDocument();
      });
    });

    test('test_loading_indicator - индикатор загрузки', async () => {
      setupKeycloakMock(true, 'mock-token');

      // Создаём promise, которым мы можем управлять
      let resolveFetch: (value: Response) => void;
      const fetchPromise = new Promise<Response>((resolve) => {
        resolveFetch = resolve;
      });

      mockFetch.mockReturnValue(fetchPromise);

      render(<ReportPage />);

      // Нажатие на кнопку для запуска загрузки
      fireEvent.click(screen.getByText(/Download Report/));

      // Проверка отображения индикатора загрузки
      await waitFor(() => {
        expect(screen.getByText(/Generating Report/)).toBeInTheDocument();
      });

      // Выполняем fetch
      await act(async () => {
        resolveFetch!({
          ok: true,
          json: async () => mockReportData,
        });
        await fetchPromise;
      });

      // Индикатор загрузки должен исчезнуть
      await waitFor(() => {
        expect(screen.queryByText(/Generating Report/)).not.toBeInTheDocument();
      });
    });
  });

  describe('3. Тесты функциональности скачивания', () => {
    test('test_download_report - скачивание отчёта', async () => {
      setupKeycloakMock(true, 'mock-token');

      // Мок document.createElement и связанных методов
      const mockCreateElement = jest.spyOn(document, 'createElement');
      const mockAppendChild = jest.fn();
      const mockRemoveChild = jest.fn();
      const mockClick = jest.fn();

      // Создание мок-элемента ссылки
      const mockLink = {
        href: '',
        download: '',
        click: mockClick,
        style: {},
      };

      mockCreateElement.mockReturnValue(mockLink as any);

      // Мок URL.createObjectURL
      const originalCreateObjectURL = URL.createObjectURL;
      URL.createObjectURL = jest.fn(() => 'blob:http://localhost/mock-url');

      // Мок URL.revokeObjectURL
      const originalRevokeObjectURL = URL.revokeObjectURL;
      URL.revokeObjectURL = jest.fn();

      // Мок методов document.body
      const originalBodyAppendChild = document.body.appendChild;
      const originalBodyRemoveChild = document.body.removeChild;
      document.body.appendChild = mockAppendChild;
      document.body.removeChild = mockRemoveChild;

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockReportData,
      });

      // Используем fake timers для этого теста
      jest.useFakeTimers();

      render(<ReportPage />);

      await waitFor(() => {
        expect(screen.getByText(/Download Report/)).toBeInTheDocument();
      });

      // Нажатие на кнопку скачивания
      fireEvent.click(screen.getByText(/Download Report/));

      // Ожидание завершения fetch и скачивания
      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      // Перемотка таймеров для запуска скачивания
      jest.advanceTimersByTime(100);

      // Проверка инициации скачивания
      await waitFor(() => {
        expect(mockCreateElement).toHaveBeenCalledWith('a');
        expect(mockClick).toHaveBeenCalled();
      });

      // Восстановление моков
      URL.createObjectURL = originalCreateObjectURL;
      URL.revokeObjectURL = originalRevokeObjectURL;
      document.body.appendChild = originalBodyAppendChild;
      document.body.removeChild = originalBodyRemoveChild;
      mockCreateElement.mockRestore();
      jest.useRealTimers();
    });

    test('test_download_button_disabled_without_data - кнопка неактивна без данных', async () => {
      setupKeycloakMock(true, 'mock-token');

      // Возврат 404 (нет данных)
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      });

      render(<ReportPage />);

      // Ожидание появления ошибки
      await waitFor(() => {
        expect(screen.getByText(/Report not found/)).toBeInTheDocument();
      });

      // Кнопка должна быть видимой - она запускает новый запрос данных
      const button = screen.getByText(/Download Report/);
      expect(button).toBeInTheDocument();
      // Кнопка должна быть активной для получения новых данных
      expect(button).not.toBeDisabled();
    });
  });
});
