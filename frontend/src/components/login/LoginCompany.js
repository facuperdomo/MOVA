// src/components/login/LoginCompany.jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './loginCompanyStyle.css';
import { customFetch } from '../../utils/api';
import logo from '../../assets/logo-login.png';

export default function LoginCompany() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState(false);
  const [msg, setMsg] = useState('');
  const [isPressed, setIsPressed] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const showError = (message) => {
    setErr(true);
    setMsg(message);
  };

  const hideError = () => {
    setErr(false);
    setMsg('');
  };

  const handleMouseDown = () => setIsPressed(true);
  const handleMouseUp = () => setIsPressed(false);

  const loginAction = async (e) => {
    e.preventDefault();
    hideError();

    if (!username.trim()) {
      showError('El usuario ingresado no puede ser vacío.');
      return;
    }

    // Limpiar cualquier sesión previa
    [
      'token',
      'role',
      'companyId',
      'isAdmin',
      'deviceId',
      'selectedCashBoxId',
      'branchId'
    ].forEach(key => localStorage.removeItem(key));

    try {
      setLoading(true);
      const data = await customFetch('/auth/loginBranch', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
        skipRefresh: true
      });

      // Guardar token de branch
      localStorage.setItem('token', data.token);

      // Extraer y guardar branchId
      try {
        const payload = JSON.parse(atob(data.token.split('.')[1]));
        if (payload.branchId) {
          localStorage.setItem('branchId', payload.branchId.toString());
        }
      } catch {
        console.warn('No se pudo extraer branchId del token de empresa.');
      }

      // Redirigir al login de usuario
      navigate('/loginUser', { replace: true });

    } catch (err) {
      showError(err.message);
    } finally {
      setLoading(false);
      setIsPressed(false);
    }
  };

  return (
    <div className='login-form'>
      <img src={logo} alt="Logo" className="logo-login" />
      <form id='containerLoginForm'>
        <label id='login'>Ingresar Empresa</label>
        <input
          className='username'
          type='text'
          id='username'
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder='Usuario de la Empresa'
          required
          disabled={loading}
        />
        <input
          className='password'
          type='password'
          id='password'
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder='Contraseña'
          disabled={loading}
        />
        <input
          className={`loginButton ${isPressed ? 'pressed' : ''}`}
          type='button'
          id='loginButton'
          onClick={loginAction}
          onMouseDown={handleMouseDown}
          onMouseUp={handleMouseUp}
          value={loading ? 'Ingresando…' : 'Ingresar'}
          disabled={loading}
        />
      </form>
      {err && (
        <div id='msgValidateLogin' className='errorValidateLogin'>
          <div id='errorSpan'><span>Error</span></div>
          {msg}
        </div>
      )}
    </div>
  );
}
