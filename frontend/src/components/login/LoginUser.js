// src/components/login/LoginUser.jsx
import React, { useState } from 'react';
import './loginUserStyle.css';
import { useNavigate } from 'react-router-dom';
import { customFetch } from '../../utils/api';
import logo from '../../assets/logo-login.png';

export default function LoginUser() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState(false);
  const [msg, setMsg] = useState('');
  const [isPressed, setIsPressed] = useState(false);

  // Para el modal de forzar login
  const [showForceModal, setShowForceModal] = useState(false);
  const [pendingBranchId, setPendingBranchId] = useState(null);

  const navigate = useNavigate();

  const showError = message => {
    setErr(true);
    setMsg(message);
  };
  const hideError = () => {
    setErr(false);
    setMsg('');
  };

  const handleMouseDown = () => setIsPressed(true);
  const handleMouseUp = () => setIsPressed(false);

  // Ejecuta el login (normal o forzado)
  const doLogin = async (force, branchId) => {
    const url = force
      ? '/auth/loginUser?forzarLogin=true'
      : '/auth/loginUser';
    const data = await customFetch(url, {
      method: 'POST',
      body: JSON.stringify({ username, password, branchId }),
      skipRefresh: true
    });

    // Guardar datos en localStorage
    localStorage.setItem('token', data.token);
    localStorage.setItem('role', data.role);
    localStorage.setItem('companyId', data.companyId);
    localStorage.setItem('isAdmin', data.role === 'ADMIN');
    localStorage.setItem('userId', data.userId);

    // Redirigir según rol
    if (data.role === 'SUPERADMIN') navigate('/superadmin-dashboard', { replace: true });
    else if (data.role === 'ADMIN') navigate('/admin-options', { replace: true });
    else if (data.role === 'USER') navigate('/dashboard', { replace: true });
    else if (data.role === 'KITCHEN') navigate('/kitchen-dashboard', { replace: true });
  };

  const loginAction = async e => {
    hideError();
    e.preventDefault();

    if (!username.trim()) {
      showError('El usuario ingresado no puede ser vacío');
      return;
    }

    const raw = localStorage.getItem('token');
    if (!raw) {
      showError('No se encontró token. Inicie sesión en la empresa primero.');
      return;
    }

    let payload;
    try {
      payload = JSON.parse(atob(raw.split('.')[1]));
    } catch {
      showError('Token inválido. Intente iniciar sesión nuevamente.');
      return;
    }

    const branchId = payload.branchId;
    if (!branchId) {
      showError('El token no contiene información de la sucursal.');
      return;
    }
    // Guardamos para usar en el modal
    setPendingBranchId(branchId);

    try {
      await doLogin(false, branchId);
    } catch (error) {
      const status = error.status || error.data?.status;
      const serverMsg = error.data?.message || error.message;

      if (status === 409) {
        // conflicto → mostramos modal
        setShowForceModal(true);
      } else if (serverMsg.toLowerCase().includes('bad credentials')) {
        showError('No existe ese usuario para esta sucursal o la contraseña es incorrecta');
      } else {
        showError(serverMsg);
      }
    }
  };

  const handleForceConfirm = async () => {
    setShowForceModal(false);
    if (!pendingBranchId) return;
    try {
      await doLogin(true, pendingBranchId);
    } catch (error) {
      const serverMsg = error.data?.message || error.message;
      showError(serverMsg);
    }
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
          onChange={e => setUsername(e.target.value)}
          placeholder='Usuario de la Empresa'
        />
        <input
          className='password'
          type='password'
          id='password'
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder='Contraseña'
        />
        <input
          className={`loginButton ${isPressed ? 'pressed' : ''}`}
          type='button'
          id='loginButton'
          onMouseDown={handleMouseDown}
          onMouseUp={handleMouseUp}
          onClick={loginAction}
          value='Ingresar'
        />
      </form>

      {err && (
        <div id='msgValidateLogin' className='errorValidateLogin'>
          <div id='errorSpan'><span>Error</span></div>
          {msg}
        </div>
      )}

      {/* Modal de “Forzar Login” */}
      {showForceModal && (
        <div className="force-overlay">
          <div className="force-modal">
            <h3>Sesión Activa Detectada</h3>
            <p>Ya hay otra sesión activa con este usuario.<br />¿Cerrar la anterior y entrar aquí?</p>
            <div className="force-buttons">
              <button
                type="button"
                className="cancel-btn"
                onClick={() => setShowForceModal(false)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="force-btn"
                onClick={handleForceConfirm}
              >
                Cerrar sesión remota y entrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
