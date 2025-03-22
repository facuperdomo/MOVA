import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { customFetch } from "../../utils/api";
import { X } from "lucide-react";
import "./adminOptionsStyle.css";
import { API_URL } from '../../config/apiConfig';

const AdminOptions = () => {
  const navigate = useNavigate();
  const [initialCash, setInitialCash] = useState("");
  const [isCashRegisterOpen, setIsCashRegisterOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [cashSummary, setCashSummary] = useState(null);
  const [showClosePopup, setShowClosePopup] = useState(false);
  const [showSummaryPopup, setShowSummaryPopup] = useState(false);

  // ✅ Calculadora
  const [showCalculatorPopup, setShowCalculatorPopup] = useState(false);
  const [calculatorInput, setCalculatorInput] = useState("");
  const [realAmount, setRealAmount] = useState("");
  const [difference, setDifference] = useState(null);

  const checkCashRegisterStatus = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/cash-register/status`);
      if (typeof response === "string") return;
      setIsCashRegisterOpen(response);
    } catch (error) {
      console.error("❌ Error al obtener estado de caja:", error);
    }
  };

  useEffect(() => {
    checkCashRegisterStatus();
  }, []);

  const openCashRegister = async () => {
    if (!initialCash || initialCash <= 0) {
      alert("❌ El monto inicial debe ser mayor a 0.");
      return;
    }
    try {
      await customFetch(`${API_URL}/api/cash-register/open`, {
        method: "POST",
        body: JSON.stringify({ initialAmount: initialCash }),
      });
      setIsCashRegisterOpen(true);
    } catch (error) {
      console.error("❌ Error al abrir caja:", error);
    }
  };

  const closeCashRegister = async () => {
    setLoading(true);
    try {
      const response = await customFetch(`${API_URL}/api/cash-register/close`, {
        method: "POST",
      });

      if (response && response.totalSold !== undefined) {
        setCashSummary(response);
        setShowSummaryPopup(true);
      }
      setIsCashRegisterOpen(false);
    } catch (error) {
      console.error("❌ Error al cerrar caja:", error);
      alert(`❌ No se pudo cerrar la caja. Motivo: ${error.message}`);
    } finally {
      setLoading(false);
      setShowClosePopup(false);
    }
  };

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("isAdmin");
    window.location.href = "/login";
  };

  // ✅ Calcular diferencia automáticamente
  useEffect(() => {
    if (cashSummary && realAmount !== "") {
      const diff = parseFloat(realAmount) - cashSummary.expectedAmount;
      setDifference(diff);
    } else {
      setDifference(null);
    }
  }, [realAmount, cashSummary]);

  // ✅ Calcular resultado de la calculadora
  const calculateResult = () => {
    try {
      const result = eval(calculatorInput);
      setRealAmount(result.toFixed(2));
      setShowCalculatorPopup(false);
    } catch {
      alert("❌ Expresión inválida");
    }
  };

  return (
    <div className="admin-options">
      {/* ✅ Menú Lateral */}
      <nav className={`sidebar ${isMenuOpen ? "open" : ""}`}>
        <div className="menu-toggle" onClick={() => setIsMenuOpen(!isMenuOpen)}>
          ☰
        </div>
        <ul>
          <li onClick={() => navigate("/dashboard")}>🛒 {isMenuOpen && <span>Venta</span>}</li>
          <li onClick={() => navigate("/statistics")}>📊 {isMenuOpen && <span>Estadísticas</span>}</li>
          <li onClick={() => navigate("/adminProducts")}>📦 {isMenuOpen && <span>Productos</span>}</li>
          <li onClick={logout}>🚪 {isMenuOpen && <span>Cerrar Sesión</span>}</li>
        </ul>
      </nav>

      {/* ✅ Contenido principal */}
      <div className="admin-container">
        <h2>Bienvenido, Administrador</h2>

        {isCashRegisterOpen === null ? (
          <p>⌛ Cargando estado de la caja...</p>
        ) : isCashRegisterOpen ? (
          <>
            <p>✅ La caja ya está abierta.</p>
            <button className="close-cash-btn" onClick={() => setShowClosePopup(true)}>
              {loading ? "Cerrando..." : "Cerrar Caja"}
            </button>
          </>
        ) : (
          <>
            <label>Ingrese el monto inicial en caja:</label>
            <input
              type="text"
              value={initialCash === "" ? "" : `$${initialCash}`}
              onChange={(e) => setInitialCash(e.target.value.replace(/\D/g, ""))}
              placeholder="$ Ingrese monto"
            />
            <button className="open-cash-btn" onClick={openCashRegister}>
              Abrir Caja
            </button>
          </>
        )}
      </div>

      {/* ✅ Popup de Confirmación */}
      {showClosePopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={() => setShowClosePopup(false)} />
            <h2>¿Cerrar la caja?</h2>
            <p>Se generará el resumen de cierre con el total vendido y el monto esperado en la caja.</p>
            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={closeCashRegister}>
                ✅ Confirmar
              </button>
              <button className="popup-btn popup-btn-qr" onClick={() => setShowClosePopup(false)}>
                ❌ Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ✅ Popup de Resumen */}
      {showSummaryPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={() => setShowSummaryPopup(false)} />
            <h2>💸 Resumen de Cierre</h2>
            <p>Total vendido: <strong>${cashSummary.totalSold}</strong></p>
            <p>Total esperado en caja: <strong>${cashSummary.expectedAmount}</strong></p>

            <button className="calculator-btn" onClick={() => setShowCalculatorPopup(true)}>
              🧮 Abrir Calculadora
            </button>

            {/* ✅ Resultado de la Calculadora */}
            {realAmount && (
              <div className="real-amount-result">
                <p>Monto real ingresado: <strong>${realAmount}</strong></p>
                <p className={`difference ${difference >= 0 ? "correct" : "incorrect"}`}>
                  Diferencia: <strong>${difference}</strong>
                </p>
              </div>
            )}

            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={() => setShowSummaryPopup(false)}>
                ✅ Aceptar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ✅ Popup de Calculadora */}
      {showCalculatorPopup && (
        <div className="popup-overlay">
          <div className="popup-content calculator-popup">
            <X className="popup-close" size={32} onClick={() => setShowCalculatorPopup(false)} />
            <h2>🧮 Calculadora</h2>
            <input
              type="text"
              value={calculatorInput}
              onChange={(e) => setCalculatorInput(e.target.value)}
              placeholder="Ej: 500+100+300"
            />
            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={calculateResult}>
                ✅ Calcular
              </button>
              <button className="popup-btn popup-btn-qr" onClick={() => setShowCalculatorPopup(false)}>
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

