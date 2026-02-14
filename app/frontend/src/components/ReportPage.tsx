import React, { useState } from 'react';
import { useKeycloak } from '@react-keycloak/web';

const ReportPage: React.FC = () => {
  const { keycloak, initialized } = useKeycloak();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  const handleLogout = () => {
    keycloak.logout();
  };

  if (!initialized) {
    return <div>Loading...</div>;
  }

  if (!keycloak.authenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <div className="p-8 bg-white rounded-lg shadow-md text-center">
          <h1 className="text-2xl font-bold mb-6">Welcome to Reports</h1>
          <p className="text-gray-600 mb-6">Please login to access your reports</p>
          <button
            onClick={() => keycloak.login()}
            className="px-6 py-3 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
          >
            Login with PKCE
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
      <div className="p-8 bg-white rounded-lg shadow-md w-full max-w-2xl">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold">Usage Reports</h1>
          <button
            onClick={handleLogout}
            className="px-4 py-2 bg-gray-500 text-white rounded hover:bg-gray-600 text-sm"
          >
            Logout
          </button>
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

          {keycloak.token && (
            <div className="p-4 bg-gray-50 rounded border border-gray-200">
              <h3 className="font-semibold mb-2">User Info</h3>
              <div className="text-sm text-gray-600 space-y-1">
                <p><strong>Username:</strong> {keycloak.subject || 'N/A'}</p>
                <p><strong>Realm Roles:</strong> {keycloak.realmAccess?.roles?.join(', ') || 'None'}</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ReportPage;