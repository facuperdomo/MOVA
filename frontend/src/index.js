import './popup-global.css';                         // <— este existe ahora
import './components/dashboardComp/dashboardStyle.css';  // <— comprueba que esta ruta sea correcta
import './components/admin/adminOptionsStyle.css'; 
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
