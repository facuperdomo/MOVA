import React, { useState } from 'react';
import './loginUserStyle.css';
import { useNavigate } from 'react-router-dom';
import { API_URL } from '../../config/apiConfig';

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
      // Recupera el companyId guardado en localStorage
      const companyId = localStorage.getItem('companyId');
      if (!companyId) {
        throw new Error('No se encontró el ID de la empresa. Por favor, inicie sesión en la empresa primero.');
      }

      const response = await fetch(`${API_URL}/auth/loginUser`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, companyId }),
      });

      if (!response.ok) {
        throw new Error('Usuario o contraseña incorrectos.');
      }

      const data = await response.json();
      console.log("🔍 Respuesta del backend:", data); // 🔍 Verifica qué responde el backend

      localStorage.setItem('token', data.token);
      localStorage.setItem('role', data.role); // Guardar el rol del usuario
      localStorage.setItem('companyId', data.companyId);
      localStorage.setItem('isAdmin', data.role === 'ADMIN'); // ✅ Guarda si es admin
      console.log("✅ Rol guardado en localStorage:", data.role); // 🔍 Verifica que se guardó bien

      // 🔹 Redirigir correctamente después del login
      if (data.role === 'ADMIN') {
        navigate('/admin-options', { replace: true }); // ✅ Lleva a admin-options sin dejar historial
      } else if (data.role === 'USER') {
        navigate('/dashboard', { replace: true }); // ✅ Lleva a dashboard si es usuario normal
      } else if (data.role === 'KITCHEN') {
        navigate('/kitchen-dashboard', { replace: true }); // ✅ Lleva a la pantalla de comanderia si es usuario cocina
      } 

    } catch (err) {
      showError(err.message);
    }
  };

  return (
    <div className='login-form'>
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
