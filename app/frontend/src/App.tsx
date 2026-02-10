import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ReportPage from './components/ReportPage';

const App: React.FC = () => {
  const loginWithBFF = () => {
    window.location.href = `${process.env.REACT_APP_BFF_URL}/auth/login`;
  };

  const checkAuth = () => {
    const cookies = document.cookie.split(';');
    const sessionCookie = cookies.find(c => c.trim().startsWith('BIONICPRO_SESSION='));
    return !!sessionCookie;
  };

  return (
    <BrowserRouter>
      <Routes>
        <Route 
          path="/login" 
          element={<div>Redirecting to BFF login...</div>} 
        />
        <Route 
          path="/" 
          element={checkAuth() ? <ReportPage /> : <Navigate to="/login" />} 
        />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;