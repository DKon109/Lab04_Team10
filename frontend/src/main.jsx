import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import PortfolioDemo from './PortfolioDemo';
import './styles/portfolio.css';
import './styles/global.css';
import './styles/experience.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {import.meta.env.VITE_PORTFOLIO_DEMO === 'true' ? <PortfolioDemo /> : <App />}
  </React.StrictMode>
);
