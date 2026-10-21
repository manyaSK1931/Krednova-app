import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { NotificationsProvider } from './context/NotificationsContext';
import { ToastProvider } from './components/Toast';
import App from './App';
import { warmUp } from './services/api';
import './index.css';

warmUp(); // starts waking a sleeping backend while the page is still loading

createRoot(document.getElementById('root')).render(
  <BrowserRouter><AuthProvider><NotificationsProvider><ToastProvider><App /></ToastProvider></NotificationsProvider></AuthProvider></BrowserRouter>
);
