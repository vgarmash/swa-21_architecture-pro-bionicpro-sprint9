import React, { useState, useEffect } from 'react';
import axios from 'axios';

export const ReportPage: React.FC = () => {
  const [reports, setReports] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchReports = async () => {
    try {
      const response = await axios.get(`${process.env.REACT_APP_API_URL}/reports`, {
        credentials: 'include'
      });
      setReports(response.data);
    } catch (error) {
      if (error.response?.status === 401) {
        window.location.href = `${process.env.REACT_APP_BFF_URL}/auth/login`;
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReports();
  }, []);

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      <h1>Reports</h1>
      <button onClick={fetchReports}>Refresh</button>
      <ul>
        {reports.map(report => (
          <li key={report.id}>{report.name}</li>
        ))}
      </ul>
    </div>
  );
};