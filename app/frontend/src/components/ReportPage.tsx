import React, { useState } from 'react';

const ReportPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Имитация работы с новым сервисом (без Keycloak)
  const downloadReport = async () => {
    try {
      setLoading(true);
      setError(null);

      // Имитация вызова API нового сервиса
      // Здесь будет реальная логика работы с новым backend-сервисом
      const response = await fetch(`${process.env.REACT_APP_API_URL}/reports`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          // Вместо токена теперь будет использоваться новый метод аутентификации
        }
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      console.log('Report data:', data);

    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  // Имитация входа в систему
  const handleLogin = () => {
    // В реальном приложении здесь будет логика входа через новый сервис
    console.log('Login using new service');
  };

  // Имитация выхода из системы
  const handleLogout = () => {
    // В реальном приложении здесь будет логика выхода
    console.log('Logout using new service');
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
      <div className="p-8 bg-white rounded-lg shadow-md w-full max-w-2xl">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold">Usage Reports</h1>
        </div>

        <div className="space-y-4">
          <button
            onClick={downloadReport}
            disabled={loading}
            className={`w-full px-4 py-3 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors ${
              loading ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            {loading ? (
              <div className="flex items-center justify-center">
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-2"></div>
                Generating Report...
              </div>
            ) : (
              'Download Report'
            )}
          </button>

          {error && (
            <div className="p-4 bg-red-100 text-red-700 rounded border border-red-300">
              <p className="font-semibold">Error</p>
              <p>{error}</p>
            </div>
          )}

          <div className="p-4 bg-gray-50 rounded border border-gray-200">
            <h3 className="font-semibold mb-2">System Status</h3>
            <div className="text-sm text-gray-600 space-y-1">
              <p><strong>Authentication:</strong> New service</p>
              <p><strong>Status:</strong> Ready to use</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ReportPage;