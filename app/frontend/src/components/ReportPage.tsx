import React, { useState, useEffect } from 'react';
import { useKeycloak } from '@react-keycloak/web';

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

const ReportPage: React.FC = () => {
  const { keycloak, initialized } = useKeycloak();
  const [loading, setLoading] = useState(false);
  const [reportData, setReportData] = useState<ReportData | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchReport = async (showLoading = true) => {
    if (!keycloak?.token) {
      setError('Not authenticated');
      return;
    }

    try {
      if (showLoading) setLoading(true);
      setError(null);

      const response = await fetch(`${process.env.REACT_APP_API_URL}/reports`, {
        headers: {
          'Authorization': `Bearer ${keycloak.token}`
        }
      });

      if (!response.ok) {
        if (response.status === 401) {
          setError('Unauthorized. Please login again.');
          keycloak.logout();
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

  // Автоматическая загрузка данных при монтировании компонента
  useEffect(() => {
    if (initialized && keycloak.authenticated) {
      fetchReport(false);
    }
  }, [initialized, keycloak.authenticated]);

  const downloadReport = async () => {
    if (!keycloak?.token) {
      setError('Not authenticated');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const response = await fetch(`${process.env.REACT_APP_API_URL}/reports`, {
        headers: {
          'Authorization': `Bearer ${keycloak.token}`
        }
      });

      if (!response.ok) {
        if (response.status === 401) {
          setError('Unauthorized. Please login again.');
          keycloak.logout();
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

      // Скачивание отчёта в JSON формате
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `report_${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  if (!initialized) {
    return <div>Loading...</div>;
  }

  if (!keycloak.authenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <button
          onClick={() => keycloak.login()}
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
        <h1 className="text-2xl font-bold mb-6">Usage Reports</h1>
        
        <button
          onClick={downloadReport}
          disabled={loading}
          className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 mb-6 ${
            loading ? 'opacity-50 cursor-not-allowed' : ''
          }`}
        >
          {loading ? 'Generating Report...' : 'Download Report (JSON)'}
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
              <h2 className="text-xl font-semibold mb-3">User Information</h2>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="font-medium">Name:</span> {reportData.user.name}
                </div>
                <div>
                  <span className="font-medium">Email:</span> {reportData.user.email}
                </div>
                <div>
                  <span className="font-medium">Age:</span> {reportData.user.age}
                </div>
                <div>
                  <span className="font-medium">Gender:</span> {reportData.user.gender}
                </div>
                <div>
                  <span className="font-medium">Country:</span> {reportData.user.country}
                </div>
              </div>
            </div>

            {/* Информация о протезе */}
            <div className="border rounded-lg p-4">
              <h2 className="text-xl font-semibold mb-3">Prosthesis Information</h2>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="font-medium">Type:</span> {reportData.prosthesis.type}
                </div>
                <div>
                  <span className="font-medium">Muscle Group:</span> {reportData.prosthesis.muscleGroup}
                </div>
              </div>
            </div>

            {/* Статистика использования */}
            <div className="border rounded-lg p-4">
              <h2 className="text-xl font-semibold mb-3">Usage Statistics</h2>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="font-medium">Total Sessions:</span> {reportData.statistics.totalSessions}
                </div>
                <div>
                  <span className="font-medium">Total Hours:</span> {reportData.statistics.totalHours}
                </div>
                <div>
                  <span className="font-medium">Average Amplitude:</span> {reportData.statistics.averageAmplitude}
                </div>
                <div>
                  <span className="font-medium">Average Frequency:</span> {reportData.statistics.averageFrequency}
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
