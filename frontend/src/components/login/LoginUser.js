import React, { useState } from 'react';
import './loginUserStyle.css';
import { useNavigate } from 'react-router-dom';
import { API_URL } from '../../config/apiConfig';
import { customFetch } from '../../utils/api';
import logo from '../../assets/logo-login.png';

export default function LoginUser() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState(false);
  const [msg, setMsg] = useState('');
  const [isPressed, setIsPressed] = useState(false);

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
    hideError();
    e.preventDefault();

    if (username.trim() === '') {
      showError('El usuario ingresado no puede ser vacío');
      return;
    }

    try {
      const token = localStorage.getItem('token');
      if (!token) {
        showError('No se encontró token. Inicie sesión en la empresa primero.');
        return;
      }

      let payload;
      try {
        payload = JSON.parse(atob(token.split('.')[1]));
      } catch (e) {
        showError('Token inválido. Intente iniciar sesión nuevamente.');
        return;
      }

      const branchId = payload.branchId;
      if (!branchId) {
        showError('El token no contiene información de la sucursal.');
        return;
      }

      const data = await customFetch("/auth/loginUser", {
        method: 'POST',
        body: JSON.stringify({ username, password, branchId }),
        skipRefresh: true // 🧠 EVITA INTENTAR REFRESH
      });

      console.log("🔍 Respuesta del backend:", data);
      localStorage.setItem('token', data.token);
      localStorage.setItem('role', data.role);
      localStorage.setItem('companyId', data.companyId);
      localStorage.setItem('isAdmin', data.role === 'ADMIN');
      
      // Esperá un frame para asegurar que `localStorage` esté actualizado
      await new Promise((r) => setTimeout(r, 0));

      if (data.role === 'SUPERADMIN') {
        navigate('/superadmin-dashboard', { replace: true });
      } else if (data.role === 'ADMIN') {
        console.log("✅ Login terminado")
        navigate('/admin-options', { replace: true });
      } else if (data.role === 'USER') {
        navigate('/dashboard', { replace: true });
      } else if (data.role === 'KITCHEN') {
        navigate('/kitchen-dashboard', { replace: true });
      }

    } catch (err) {
      showError(err.message);
    }
    console.log("✅ Login terminado")
  };

  return (
    <div className='login-form'>
      <img src={logo} alt="Logo" className="logo-login" />
      <form id='containerLoginForm'>
        <label id='login'>Ingresar Usuario</label>
        <input
          className='username'
          type='text'
          id='username'
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder='Usuario de la Empresa'
          required
        />
        <input
          className='password'
          type='password'
          id='password'
          value={password}
          placeholder='Contraseña'
          onChange={(e) => setPassword(e.target.value)}
        />
        <input
          className={`loginButton ${isPressed ? 'pressed' : ''}`}
          type='button'
          id='loginButton'
          onClick={loginAction}
          onMouseDown={handleMouseDown}
          onMouseUp={handleMouseUp}
          value='Ingresar'
        />
      </form>
      <div id='msgValidateLogin' className={err ? 'errorValidateLogin' : ''}>
        <div id='errorSpan'>
          <span>Error</span>
        </div>
        {msg}
      </div>
    </div>
  );
}
