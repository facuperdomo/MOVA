import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_URL } from '../../config/apiConfig';
import './loginUserStyle.css';

const LoginSuperAdmin = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState(false);
  const [msg, setMsg] = useState('');
  const navigate = useNavigate();

  const showError = (message) => {
    setErr(true);
    setMsg(message);
  };

  const handleLogin = async () => {
    setErr(false);
    try {
      const res = await fetch(`${API_URL}/auth/loginUser`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (!res.ok) throw new Error('Usuario o contraseña incorrectos.');

      const data = await res.json();
      if (data.role !== 'SUPERADMIN') throw new Error('Este usuario no es SUPERADMIN.');

      localStorage.setItem('token', data.token);
      localStorage.setItem('role', data.role);
      navigate('/superadmin-dashboard');
    } catch (err) {
      showError(err.message);
    }
  };

  return (
    <div className="login-form">
      <form id="containerLoginForm">
        <label id="login">Login SuperAdmin</label>
        <input
          id="username"
          type="text"
          placeholder="Usuario"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <input
          id="password"
          type="password"
          placeholder="Contraseña"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <input
          id="loginButton"
          type="button"
          value="Ingresar"
          onClick={handleLogin}
        />
      </form>
      {err && (
        <div id="msgValidateLogin" className="errorValidateLogin">
          <div id="errorSpan"><span>Error</span></div>
          {msg}
        </div>
      )}
    </div>
  );
};

export default LoginSuperAdmin;
