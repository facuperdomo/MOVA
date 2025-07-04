// src/components/admin/AdminOptions.js
import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { customFetch } from "../../utils/api";
import { X } from "lucide-react";
import "./adminOptionsStyle.css";
import { API_URL } from "../../config/apiConfig";
import SelectDeviceModal from "../common/SelectDeviceModal";

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

  const [cashBoxes, setCashBoxes] = useState([]);    // lista de cajas
  const [allUsers, setAllUsers] = useState([]);    // lista de todos los vendedores
  const [assignedUserIds, setAssignedUserIds] = useState([]); // ids asignados a la caja seleccionada
  const [currentBox, setCurrentBox] = useState(null);  // caja que estamos gestionando
  const [selectedBox, setSelectedBox] = useState(null); // la que abrimos / cerramos
  const [showManageBoxesModal, setShowManageBoxesModal] = useState(false);
  const [showAssignModal, setShowAssignModal] = useState(false);

  const [showCreateBoxModal, setShowCreateBoxModal] = useState(false);
  const [newBoxCode, setNewBoxCode] = useState("");
  const [newBoxName, setNewBoxName] = useState("");

  // estado para mostrar el popup de error al asignar
  const [showAssignErrorPopup, setShowAssignErrorPopup] = useState(false);
  // mensaje de error que viene del server
  const [assignErrorMessage, setAssignErrorMessage] = useState("");

  const [canCreateBox, setCanCreateBox] = useState(null);
  const [boxToDisable, setBoxToDisable] = useState(null);
  const [showDisableModal, setShowDisableModal] = useState(false);

  const [showLimitWarning, setShowLimitWarning] = useState(false);

  const [devices, setDevices] = useState([]);
  const [showDeviceModal, setShowDeviceModal] = useState(false);

  const LOCAL_KEY = "selectedCashBoxId";

  useEffect(() => {
    if (!cashBoxes.length) return;

    // 1) Primero intento restaurar de localStorage,
    //    en caso de que el usuario ya la hubiera seleccionado manualmente:
    const savedId = localStorage.getItem(LOCAL_KEY);
    if (savedId) {
      const box = cashBoxes.find(b => String(b.id) === savedId);
      if (box) {
        setSelectedBox(box);
        setIsCashRegisterOpen(box.isOpen);
        return;
      }
    }

    // 2) Si no había en localStorage, pregunto al servidor
    customFetch(`${API_URL}/api/cash-box/status-for-user`)
      .then(dto => {
        if (dto.open && dto.code) {
          // busco la caja por código en mi lista
          const openBox = cashBoxes.find(b => b.code === dto.code);
          if (openBox) {
            setSelectedBox(openBox);
            setIsCashRegisterOpen(true);
            // guardo también para futuras recargas
            localStorage.setItem(LOCAL_KEY, openBox.id);
          }
        }
      })
      .catch(console.error);
  }, [cashBoxes]);

  useEffect(() => {
    if (!localStorage.getItem("deviceId")) {
      // obtén branchId y token desde localStorage
      const branchId = localStorage.getItem("branchId");
      const token = localStorage.getItem("token");
      fetch(`${API_URL}/api/branches/${branchId}/devices`, {
        headers: { "Authorization": `Bearer ${token}` }
      })
        .then(r => r.json())
        .then(list => {
          setDevices(list);
          setShowDeviceModal(true);
        })
        .catch(console.error);
    }
  }, []); // sólo una vez al montar

  useEffect(() => {
    if (selectedBox != null) {
      setIsCashRegisterOpen(selectedBox.isOpen);
    }
  }, [selectedBox]);

  // 1) Chequear estado de caja al montar
  useEffect(() => {
    // Estado simple de si hay caja abierta
    customFetch(`${API_URL}/api/cash-box/status`)
      .then(resp => { if (typeof resp !== "string") setIsCashRegisterOpen(resp); })
      .catch(console.error);

    // Traer cajas y usuarios
    loadCashBoxes();
    loadAllUsers();
    loadCanCreateBox();
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

  useEffect(() => {
    if (!currentBox || !currentBox.id) return;      // ← GUARD
    console.log("🔍 Cargando asignados para caja:", currentBox.id);
    loadAssignedUsers(currentBox);
  }, [currentBox]);

  // ————————————————————— Funciones de carga —————————————————————
  async function loadCashBoxes() {
    try {
      const cajas = await customFetch(`${API_URL}/api/cash-box`);
      // filtrado inmediato:
      setCashBoxes(cajas.filter(box => box.enabled));
      await loadCanCreateBox();
    } catch (err) {
      console.error("Error al cargar cajas:", err);
    }
  }

  async function loadAllUsers() {
    try {
      const users = await customFetch(`${API_URL}/auth`);
      const filtered = users.filter(u => u.role === "USER" || u.role === "ADMIN");
      setAllUsers(filtered);
    } catch (err) {
      console.error("Error al cargar usuarios:", err);
    }
  }

  async function loadCanCreateBox() {
    try {
      const ok = await customFetch(`${API_URL}/api/cash-box/can-create`);
      setCanCreateBox(ok);
    } catch (e) {
      console.error("Error comprobando límite de cajas:", e);
    }
  }

  async function loadAssignedUsers(box) {
    try {
      console.log("→ fetch /api/cash-box/" + box.id + "/users");
      const users = await customFetch(
        `${API_URL}/api/cash-box/${box.id}/users`
      );
      console.log("← assignedUsers raw:", users);
      const ids = users.map(u => u.id);
      console.log("← assignedUserIds:", ids);
      setAssignedUserIds(ids);
    } catch (err) {
      console.error("Error al cargar asignados:", err);
    }
  }

  // ————————————————————— Asignar / Desasignar —————————————————————
  const toggleUserAssignment = async (userId) => {
    try {
      const verb = assignedUserIds.includes(userId) ? "DELETE" : "POST";
      await customFetch(
        `${API_URL}/api/cash-box/${currentBox.id}/users/${userId}`,
        { method: verb }
      );
      // recargar lista
      loadAssignedUsers(currentBox);
    } catch (err) {
      const msg = err.data?.message || err.message || "Error desconocido";
      setAssignErrorMessage(msg);
      setShowAssignErrorPopup(true);
    }
  };

  // 3) Abrir caja con validación
  const openCashRegister = async () => {
    if (!initialCash || initialCash <= 0) {
      setShowInvalidInitialPopup(true);
      return;
    }
    try {
      await customFetch(`${API_URL}/api/cash-box/open`, {
        method: "POST",
        body: JSON.stringify({ code: selectedBox.code, initialAmount: initialCash }),
      });
      setIsCashRegisterOpen(true);
    } catch (err) {
      console.error("Error al abrir caja:", err);
    }
  };

  // 4) Cerrar caja (requiere conexión)
  const closeCashRegister = async () => {
    if (!selectedBox) {
      alert("Por favor, selecciona primero la caja que deseas cerrar.");
      return;
    }

    setLoading(true);
    try {
      // payload con el código de la caja y el monto de cierre que el backend exige
      const payload = {
        code: selectedBox.code,
        closingAmount: selectedBox.initialAmount + selectedBox.totalSales
      };

      const resp = await customFetch(
        `${API_URL}/api/cash-box/close`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        }
      );

      // guardamos el resumen que nos devuelve el backend
      setCashSummary(resp);
      setShowSummaryPopup(true);
      // 1) forzar que la caja ya no esté abierta
      setIsCashRegisterOpen(false);
      // 2) recargar la lista de cajas
      await loadCashBoxes();
      await loadCanCreateBox();
      // 3) actualizar el selectedBox con la versión “cerrada”
      setSelectedBox(prev => prev && ({
        ...prev,
        isOpen: false,
        closedAt: new Date().toISOString()
      }));
    } catch (err) {
      console.error(`Error al cerrar caja "${selectedBox?.code}":`, err);
      alert(`No se pudo cerrar la caja "${selectedBox?.code}":\n${err.data || err.message}`);
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
      localStorage.removeItem('deviceId');
      localStorage.removeItem(LOCAL_KEY);

      setSelectedBox(null);
      setIsCashRegisterOpen(false);

      navigate('/login', { replace: true });
    }
  };

  return (
    <div className="admin-options">
      {showDeviceModal && (
        <SelectDeviceModal
          devices={devices}
          onSelect={(id) => {
            if (id) localStorage.setItem("deviceId", id);
            setShowDeviceModal(false);
          }}
        />
      )}
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



        {!selectedBox ? (
          <p>🔍 Por favor, selecciona primero una caja para operar.</p>
        ) : isCashRegisterOpen == null ? (
          <p>⌛ Cargando estado de la caja...</p>
        ) : isCashRegisterOpen ? (
          <>
            {/* Siempre mostramos la caja seleccionada y su estado */}
            <p>
              📦 Caja seleccionada: <strong>{selectedBox.code}</strong>
            </p>
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
            {/* Caja seleccionada para abrir */}
            <p>
              📦 Caja seleccionada: <strong>{selectedBox.code}</strong>
            </p>
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
            <button
              className="open-cash-btn"
              onClick={openCashRegister}
              disabled={!initialCash || initialCash <= 0}
            >
              Abrir Caja
            </button>
          </>
        )}
      </div>
      {/* Botón para gestionar cajas */}
      <button
        className="manage-boxes-btn"
        onClick={() => {
          setShowLimitWarning(false);
          setShowManageBoxesModal(true);
        }}
      >
        ⚙️ Administrar Cajas
      </button>
      {/* ——— Modal: Lista de cajas para gestionar ——— */}
      {showManageBoxesModal && (
        <div className="popup-overlay">
          <div className="popup-content manage-boxes-modal">
            <div className="popup-header">
              <button
                className="add-btn-popup"
                onClick={async () => {
                  try {
                    const ok = await customFetch(`${API_URL}/api/cash-box/can-create`);
                    setCanCreateBox(ok);
                    if (!ok) {
                      setShowLimitWarning(true);
                    } else {
                      setShowLimitWarning(false);
                      setShowCreateBoxModal(true);
                    }
                  } catch (e) {
                    console.error("Error comprobando límite:", e);
                  }
                }}
              >
                Crear Caja
              </button>
              <h3>Cajas abiertas</h3>
              <X
                className="popup-close"
                size={24}
                onClick={() => setShowManageBoxesModal(false)}
              />
            </div>
            {showLimitWarning && (
              <p className="alert">
                ⚠️ Ya alcanzaste el máximo de cajas permitidas.
              </p>
            )}
            {/* Botón para abrir el modal de creación */}
            <ul className="box-list">
              {/* Encabezado de columnas */}
              <li className="header-row">
                <span className="column-code">Código de caja</span>
                <span className="column-actions">Acciones</span>
              </li>
              {cashBoxes
                // sólo muestro las que siguen habilitadas
                .filter(box => box.enabled)
                .map(box => (
                  <li key={box.id} className="box-row">
                    <span className="column-code">{box.code}</span>
                    <div className="box-actions">
                      <button
                        className="popup-btn"
                        onClick={() => {
                          setSelectedBox(box);
                          localStorage.setItem(LOCAL_KEY, box.id);
                          setIsCashRegisterOpen(box.isOpen);
                          setShowManageBoxesModal(false);
                        }}
                      >
                        Seleccionar
                      </button>
                      <button
                        className="popup-btn"
                        onClick={() => {
                          setCurrentBox(box);
                          loadAssignedUsers(box);
                          setShowAssignModal(true);
                        }}
                      >
                        Asignar
                      </button>
                      <button
                        className="popup-btn cancel"
                        onClick={() => {
                          setBoxToDisable(box);
                          setShowDisableModal(true);
                        }}
                      >
                        Eliminar
                      </button>
                    </div>
                  </li>
                ))}
            </ul>
          </div>
        </div>
      )}

      {/* ——— Modal: Asignar usuarios a la caja seleccionada ——— */}
      {showAssignModal && currentBox && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowAssignModal(false)}
            />
            <h3>Asignar vendedores a caja {currentBox.code}</h3>
            <div className="user-list">
              {allUsers.map(u => (
                <label key={u.id} style={{ display: "block", margin: "8px 0" }}>
                  <input
                    type="checkbox"
                    checked={Array.isArray(assignedUserIds) && assignedUserIds.includes(u.id)}
                    onChange={() => toggleUserAssignment(u.id)}
                  />
                  {u.username}
                </label>
              ))}
            </div>
            <button
              className="popup-btn"
              onClick={() => setShowAssignModal(false)}
            >
              Cerrar
            </button>
          </div>
        </div>
      )}
      {/* ——— Modal: Crear Nueva Caja ——— */}
      {showCreateBoxModal && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowCreateBoxModal(false)}
            />
            <h3>Crear Nueva Caja</h3>

            <label>Código</label>
            <input
              type="text"
              value={newBoxCode}
              onChange={e => setNewBoxCode(e.target.value)}
              placeholder="Ej: FRONT"
            />

            <label>Nombre</label>
            <input
              type="text"
              value={newBoxName}
              onChange={e => setNewBoxName(e.target.value)}
              placeholder="Ej: Caja Principal"
            />

            <div className="popup-buttons">
              <button
                className="popup-btn"
                onClick={async () => {
                  // 3.1 Primero validamos el límite
                  if (canCreateBox === false) {
                    alert("No puedes crear más cajas de las permitidas por tu plan.");
                    return;
                  }

                  // 3.2 Luego validamos inputs
                  if (!newBoxCode.trim() || !newBoxName.trim()) {
                    alert("Por favor ingresa código y nombre de la caja.");
                    return;
                  }

                  // 3.3 Si todo ok, llamamos al backend
                  try {
                    await customFetch(`${API_URL}/api/cash-box`, {
                      method: "POST",
                      body: JSON.stringify({
                        code: newBoxCode.trim(),
                        name: newBoxName.trim()
                      }),
                    });
                    await loadCashBoxes();
                    setNewBoxCode("");
                    setNewBoxName("");
                    setShowCreateBoxModal(false);
                    // refrescamos la comprobación de permiso
                    loadCanCreateBox();
                  } catch (err) {
                    console.error("Error creando caja:", err);
                    alert(err.data?.message || "No se pudo crear la caja.");
                  }
                }}
              >
                Crear
              </button>
              <button
                className="popup-btn cancel"
                onClick={() => setShowCreateBoxModal(false)}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
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

      {showAssignErrorPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowAssignErrorPopup(false)}
            />
            <h2>⚠️ No se pudo asignar</h2>
            <p>{assignErrorMessage}</p>
            <button
              className="popup-btn"
              onClick={() => setShowAssignErrorPopup(false)}
            >
              Aceptar
            </button>
          </div>
        </div>
      )}
      {/* ——— Modal: Confirmar eliminación de caja ——— */}
      {showDisableModal && boxToDisable && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={24}
              onClick={() => setShowDisableModal(false)}
            />
            <h3>Eliminar caja {boxToDisable.code}</h3>
            <p>
              ¿Seguro que deseas eliminar (deshabilitar) esta caja?<br />
              Los registros históricos se conservarán.
            </p>
            <div className="popup-buttons">
              <button
                className="popup-btn cancel"
                onClick={async () => {
                  try {
                    // Llamarás a tu nuevo endpoint PUT /api/cash-box/{id}/enabled?enabled=false
                    await customFetch(
                      `${API_URL}/api/cash-box/${boxToDisable.id}/enabled?enabled=false`,
                      { method: "PUT" }
                    );
                    await loadCashBoxes();
                    setShowDisableModal(false);
                    setBoxToDisable(null);
                  } catch (err) {
                    console.error("Error deshabilitando caja:", err);
                    alert(err.data?.message || "No se pudo eliminar la caja.");
                  }
                }}
              >
                Sí, eliminar
              </button>
              <button
                className="popup-btn"
                onClick={() => {
                  setShowDisableModal(false);
                  setBoxToDisable(null);
                }}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminOptions;
