// src/components/admin/AdminOptions.js
import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { customFetch } from "../../utils/api";
import { X } from "lucide-react";
import "./adminOptionsStyle.css";
import { API_URL } from "../../config/apiConfig";

const AdminOptions = () => {
  const navigate = useNavigate();

  // Estado de menú lateral
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  // Estado de la caja y conexión
  const [isCashRegisterOpen, setIsCashRegisterOpen] = useState(false);
  const [offline, setOffline] = useState(!navigator.onLine);

  // Popups
  const [showInvalidInitialPopup, setShowInvalidInitialPopup] = useState(false);
  const [showNoConnPopup, setShowNoConnPopup] = useState(false);
  const [showClosePopup, setShowClosePopup] = useState(false);
  const [showSummaryPopup, setShowSummaryPopup] = useState(false);

  // Monto inicial y resumen
  const [initialCash, setInitialCash] = useState("");
  const [displayValue, setDisplayValue] = useState("");
  const [cashSummary, setCashSummary] = useState(null);
  const [loading, setLoading] = useState(false);

  // Calculadora
  const [showCalculatorPopup, setShowCalculatorPopup] = useState(false);
  const [calculatorInput, setCalculatorInput] = useState("");
  const [realAmount, setRealAmount] = useState("");
  const [difference, setDifference] = useState(null);

  // 1) Chequear estado de caja al montar
  useEffect(() => {
    console.log("🎟️ Token actual:", localStorage.getItem("token"));
    customFetch(`${API_URL}/api/cash-register/status`)
      .then(resp => {
        if (typeof resp !== "string") setIsCashRegisterOpen(resp);
      })
      .catch(err => console.error("Error al obtener estado de caja:", err));
  }, []);

  // 2) Detectar cambios de conexión
  useEffect(() => {
    const goOnline = () => setOffline(false);
    const goOffline = () => setOffline(true);

    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    return () => {
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
  }, []);

  // 3) Abrir caja con validación
  const openCashRegister = async () => {
    if (!initialCash || initialCash <= 0) {
      setShowInvalidInitialPopup(true);
      return;
    }
    try {
      await customFetch(`${API_URL}/api/cash-register/open`, {
        method: "POST",
        body: JSON.stringify({ initialAmount: initialCash }),
      });
      setIsCashRegisterOpen(true);
    } catch (err) {
      console.error("Error al abrir caja:", err);
    }
  };

  // 4) Cerrar caja (requiere conexión)
  const closeCashRegister = async () => {
    setLoading(true);
    try {
      const resp = await customFetch(`${API_URL}/api/cash-register/close`, {
        method: "POST",
      });
      if (resp?.totalSold != null) {
        setCashSummary(resp);
        setShowSummaryPopup(true);
      }
      setIsCashRegisterOpen(false);
    } catch (err) {
      console.error("Error al cerrar caja:", err);
      alert(`No se pudo cerrar la caja: ${err.message}`);
    } finally {
      setLoading(false);
      setShowClosePopup(false);
    }
  };

  // 5) Diferencia calculadora
  useEffect(() => {
    if (cashSummary && realAmount !== "") {
      setDifference(parseFloat(realAmount) - cashSummary.expectedAmount);
    } else {
      setDifference(null);
    }
  }, [realAmount, cashSummary]);

  const calculateResult = () => {
    try {
      const r = eval(calculatorInput);
      setRealAmount(r.toFixed(2));
      setShowCalculatorPopup(false);
    } catch {
      alert("Expresión inválida");
    }
  };

  const handleLogout = async () => {
    try {
      // notificamos al backend para que invalide la tokenVersion
      await customFetch(`${API_URL}/auth/logout`, {
        method: 'POST'
      });
    } catch (err) {
      console.warn('No se pudo notificar el logout al backend:', err);
    } finally {
      // limpiamos siempre el localStorage
      localStorage.removeItem('token');
      localStorage.removeItem('role');
      localStorage.removeItem('isAdmin');
      localStorage.removeItem('companyId');
      navigate('/login', { replace: true });
    }
  };

  return (
    <div className="admin-options">
      {/* Sidebar */}
      <nav className={`sidebar ${isMenuOpen ? "open" : ""}`}>
        <div className="menu-toggle" onClick={() => setIsMenuOpen(v => !v)}>
          ☰
        </div>
        <ul>
          <li onClick={() => navigate("/dashboard")}>
            🛒 {isMenuOpen && <span>Venta</span>}
          </li>
          <li onClick={() => navigate("/statistics")}>
            📊 {isMenuOpen && <span>Estadísticas</span>}
          </li>
          <li onClick={() => navigate("/adminProducts")}>
            📦 {isMenuOpen && <span>Productos</span>}
          </li>
          <li onClick={handleLogout}>
            🚪 {isMenuOpen && <span>Cerrar Sesión</span>}
          </li>
        </ul>
      </nav>

      {/* Contenido principal */}
      <div className="admin-container">
        <h2>Bienvenido, Administrador</h2>

        {isCashRegisterOpen == null ? (
          <p>⌛ Cargando estado de la caja...</p>
        ) : isCashRegisterOpen ? (
          <>
            <p>✅ La caja está abierta.</p>
            <button
              className="close-cash-btn"
              disabled={offline}
              onClick={() =>
                offline ? setShowNoConnPopup(true) : setShowClosePopup(true)
              }
            >
              {loading ? "Cerrando..." : "Cerrar Caja"}
            </button>
          </>
        ) : (
          <>
            <label>Ingrese monto inicial:</label>
            <input
              type="text"
              value={displayValue}
              onChange={(e) => {
                const raw = e.target.value.replace(/\D/g, "");
                setInitialCash(raw);
                setDisplayValue(raw ? `$${raw}` : "");
              }}
              placeholder="$0"
            />
            <button className="open-cash-btn" onClick={openCashRegister}>
              Abrir Caja
            </button>
          </>
        )}
      </div>

      {/* Popup: monto inicial inválido */}
      {showInvalidInitialPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowInvalidInitialPopup(false)}
            />
            <h2>❌ Monto inválido</h2>
            <p>Debe ingresar un valor mayor a 0.</p>
            <button
              className="popup-btn"
              onClick={() => setShowInvalidInitialPopup(false)}
            >
              Aceptar
            </button>
          </div>
        </div>
      )}

      {/* Popup: necesitas conexión para cerrar */}
      {showNoConnPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowNoConnPopup(false)}
            />
            <h2>⚠️ Sin conexión</h2>
            <p>Necesitas conexión a Internet para cerrar la caja.</p>
            <button className="popup-btn" onClick={() => setShowNoConnPopup(false)}>
              Aceptar
            </button>
          </div>
        </div>
      )}

      {/* Popup: confirmar cierre */}
      {showClosePopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowClosePopup(false)}
            />
            <h2>¿Cerrar la caja?</h2>
            <p>Se generará un resumen con total vendido y monto esperado.</p>
            <div className="popup-buttons">
              <button className="popup-btn" onClick={closeCashRegister}>
                ✅ Confirmar
              </button>
              <button
                className="popup-btn cancel"
                onClick={() => setShowClosePopup(false)}
              >
                ❌ Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Popup: resumen de cierre */}
      {showSummaryPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowSummaryPopup(false)}
            />
            <h2>💸 Resumen de Cierre</h2>
            <p>
              Total vendido: <strong>${cashSummary.totalSold}</strong>
            </p>
            <p>
              Esperado en caja: <strong>${cashSummary.expectedAmount}</strong>
            </p>
            <button
              className="popup-btn blue"
              onClick={() => setShowCalculatorPopup(true)}
            >
              🧮 Abrir Calculadora
            </button>
            {realAmount && (
              <div className="real-amount-result">
                <p>Monto real: <strong>${realAmount}</strong></p>
                <p
                  className={`difference ${difference >= 0 ? "correct" : "incorrect"
                    }`}
                >
                  Dif: <strong>${difference}</strong>
                </p>
              </div>
            )}
            <div className="popup-buttons">
              <button
                className="popup-btn"
                onClick={() => setShowSummaryPopup(false)}
              >
                ✅ Aceptar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Popup: calculadora */}
      {showCalculatorPopup && (
        <div className="popup-overlay">
          <div className="popup-content calculator-popup">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowCalculatorPopup(false)}
            />
            <h2>🧮 Calculadora</h2>
            <input
              type="text"
              value={calculatorInput}
              onChange={e => setCalculatorInput(e.target.value)}
              placeholder="Ej: 500+100+300"
            />
            <div className="popup-buttons">
              <button className="popup-btn" onClick={calculateResult}>
                ✅ Calcular
              </button>
              <button
                className="popup-btn cancel"
                onClick={() => setShowCalculatorPopup(false)}
              >
                ❌ Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminOptions;
