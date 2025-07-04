// src/components/TokenVerifier.jsx
import { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import "./TokenVerifierStyle.css";
import { API_URL } from "../config/apiConfig";

const IDLE_LIMIT_MS = 30 * 60 * 1000; // 30 minutos de inactividad

export default function TokenVerifier() {
  const navigate = useNavigate();
  const [expired, setExpired] = useState(false);
  const idleTimer = useRef(null);

  useEffect(() => {
    // Función para (re)iniciar el timer de inactividad
    const resetIdleTimer = () => {
      clearTimeout(idleTimer.current);
      idleTimer.current = setTimeout(() => {
        setExpired(true);
      }, IDLE_LIMIT_MS);
    };

    // Eventos que cuentan como “actividad”
    const events = ["mousemove", "mousedown", "keydown", "touchstart"];
    events.forEach(evt => window.addEventListener(evt, resetIdleTimer));

    // Arranco el timer una vez montado
    resetIdleTimer();

    // Limpieza al desmontar
    return () => {
      clearTimeout(idleTimer.current);
      events.forEach(evt => window.removeEventListener(evt, resetIdleTimer));
    };
  }, []);

  const handleClose = async () => {
    const raw = localStorage.getItem('token');
    if (raw) {
      try {
        // 🔥 Llamo al backend para limpiar tokenVersion
        await fetch(`${API_URL}/auth/logout`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${raw}` }
        });
      } catch (e) {
        console.warn('❌ No se pudo notificar logout al backend:', e);
      }
    }

    // Ahora sí limpio todo en el cliente
    [
      'token',
      'role',
      'companyId',
      'isAdmin',
      'deviceId',
      'selectedCashBoxId',
      'branchId'
    ].forEach(k => localStorage.removeItem(k));

    setExpired(false);
    navigate('/login', { replace: true });
  };

  // Si no expiró, no renderiza nada
  if (!expired) return null;

  return (
    <div className="tv-overlay">
      <div className="tv-modal">
        <h2>Inactividad detectada</h2>
        <p>
          Has estado inactivo durante más de 30 minutos. Por tu seguridad, vuelve
          a iniciar sesión.
        </p>
        <button onClick={handleClose}>Ir al Login</button>
      </div>
    </div>
  );
}
