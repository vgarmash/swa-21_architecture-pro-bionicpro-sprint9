import React, { useEffect, useState } from 'react';
import ReportPage from './components/ReportPage';

const App: React.FC = () => {
  const [initialized, setInitialized] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Имитация инициализации (без использования Keycloak)
  useEffect(() => {
    // Имитация асинхронной инициализации
    const init = async () => {
      try {
        // Здесь можно добавить логику для работы с новым сервисом
        setInitialized(true);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Initialization error');
        setInitialized(true);
      }
    };

    init();
  }, []);

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <div className="p-8 bg-white rounded-lg shadow-md">
          <h1 className="text-2xl font-bold mb-4 text-red-600">Error</h1>
          <p className="text-gray-700">{error}</p>
        </div>
      </div>
    );
  }

  if (!initialized) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <div className="App">
      <ReportPage />
    </div>
  );
};

export default App;