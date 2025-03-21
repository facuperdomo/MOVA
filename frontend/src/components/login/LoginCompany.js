import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './loginCompanyStyle.css';

export default function LoginCompany() {
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

    if (!username.trim()) {
      showError('El usuario ingresado no puede ser vacío.');
      return;
    }

    try {
      const response = await fetch('http://localhost:8080/auth/loginCompany', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) throw new Error('Usuario o contraseña incorrectos.');
      
      const data = await response.json();
      localStorage.setItem('token', data.token); // Guarda el token del usuario
      localStorage.setItem("companyId", data.companyId); // Guarda el ID de la Empresa
      
      navigate('/loginUser'); // Redirige al login de usuario
    } catch (err) {
      showError(err.message);
    }
  };

  return (
    <div className='login-form'>
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
      {err && (
        <div id='msgValidateLogin' className='errorValidateLogin'>
          <div id='errorSpan'><span>Error</span></div>
          {msg}
        </div>
      )}
    </div>
  );
}
