// src/components/dashboard/Dashboard.js
import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import "./dashboardStyle.css";
import { ArrowLeft, Trash2, X } from "lucide-react";
import { customFetch } from "../../utils/api";
import PaymentQR from "../paymentqr/PaymentQR";
import { API_URL } from "../../config/apiConfig";

const Dashboard = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState([]);
  const [total, setTotal] = useState(0);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [showPopup, setShowPopup] = useState(false);
  const [showQR, setShowQR] = useState(false);
  const [isCashRegisterOpen, setIsCashRegisterOpen] = useState(false);

  // Estados para modo offline
  const [offline, setOffline] = useState(!navigator.onLine);
  const [showOfflinePopup, setShowOfflinePopup] = useState(false);
  const [pendingSalesCount, setPendingSalesCount] = useState(0);
  const [offlineMessage, setOfflineMessage] = useState("");

  // Estado para almacenar la última venta exitosa (online)
  const [lastSale, setLastSale] = useState(null);

  // Verificar si la caja está abierta (se espera que customFetch incluya el header Authorization)
  const checkCashRegisterStatus = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/cash-register/status`);
      setIsCashRegisterOpen(response);
    } catch (error) {
      console.error("Error al verificar la caja:", error);
    }
  };

  useEffect(() => {
    const role = localStorage.getItem("role");
    setIsAdmin(role === "ADMIN");
    fetchProducts();
    checkCashRegisterStatus();
    updatePendingSalesCount();
  }, []);

  // Manejo de eventos online/offline
  useEffect(() => {
    function handleOnline() {
      console.log("Conexión recuperada.");
      setOffline(false);
      setShowOfflinePopup(false);
      syncOfflineSales();
    }
    function handleOffline() {
      console.log("Sin conexión.");
      setOffline(true);
      setShowOfflinePopup(true);
    }
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  // Actualizar el contador de ventas pendientes
  const updatePendingSalesCount = () => {
    const offlineSales = JSON.parse(localStorage.getItem("offlineSales")) || [];
    setPendingSalesCount(offlineSales.length);
  };

  const fetchProducts = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/products`);
      if (!Array.isArray(response))
        throw new Error("La respuesta no es un array");
      const productsWithFixedImages = response.map((product) => ({
        ...product,
        image: product.image && product.image.startsWith("data:image")
          ? product.image
          : `data:image/png;base64,${product.image}`,
        imageError: false,
      }));
      setProducts(productsWithFixedImages);
    } catch (error) {
      console.error("Error al obtener productos:", error);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  const addToCart = (product) => {
    setCart((prevCart) => {
      const updatedCart = [...prevCart];
      const index = updatedCart.findIndex((item) => item.id === product.id);
      if (index !== -1) {
        updatedCart[index] = {
          ...updatedCart[index],
          quantity: updatedCart[index].quantity + 1,
        };
      } else {
        updatedCart.push({ ...product, quantity: 1 });
      }
      return updatedCart;
    });
    setTotal((prevTotal) => prevTotal + product.price);
  };

  const removeFromCart = (productId) => {
    setCart((prevCart) => {
      const updatedCart = prevCart.filter((item) => item.id !== productId);
      const newTotal = updatedCart.reduce(
        (sum, item) => sum + item.price * item.quantity,
        0
      );
      setTotal(newTotal);
      return updatedCart;
    });
  };

  const handleLogout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("role");
    localStorage.removeItem("isAdmin");
    navigate("/login");
  };

  // Lógica de pago: se abre el popup de selección de pago
  const handlePayment = () => {
    setShowPopup(true);
    setShowQR(false);
  };

  const closePopup = () => {
    setShowPopup(false);
    setShowQR(false);
  };

  // Registrar venta con pago en efectivo
  const handleCashPayment = async () => {
    const saleData = {
      totalAmount: total,
      paymentMethod: "CASH",
      dateTime: new Date().toISOString(),
      items: cart.map((item) => ({
        productId: item.id,
        quantity: item.quantity,
        unitPrice: item.price,
      })),
    };

    const token = localStorage.getItem("token");

    if (offline) {
      storeOfflineSale(saleData);
      setShowPopup(false);
      setCart([]);
      setTotal(0);
      updatePendingSalesCount();
      setOfflineMessage("Estás sin conexión. La venta se guardó localmente y se sincronizará al reconectarte.");
      return;
    }

    try {
      const response = await customFetch(`${API_URL}/api/sales`, {
        method: "POST",
        body: JSON.stringify(saleData),
      });
      console.log("Venta registrada exitosamente en la base de datos");
      setLastSale(response);
      setShowPopup(false);
      setCart([]);
      setTotal(0);
      setOfflineMessage("");
    } catch (error) {
      console.error("Error al guardar la venta:", error);
      alert("Ocurrió un error al procesar la venta. Inténtelo de nuevo.");
    }
  };

  // Función para deshacer la última venta registrada
  const undoLastSale = async () => {
    if (!lastSale) {
      alert("No hay ventas para deshacer.");
      return;
    }
    try {
      await customFetch(`${API_URL}/api/statistics/cancel-sale/${lastSale.id}`, {
        method: "PUT",
      });
      alert("La última venta ha sido deshecha.");
      setLastSale(null);
    } catch (error) {
      console.error("Error al deshacer la venta:", error);
      alert("No se pudo deshacer la última venta. Inténtelo de nuevo.");
    }
  };

  // Almacenar venta offline en localStorage (para producción se recomienda IndexedDB)
  const storeOfflineSale = (saleData) => {
    try {
      const offlineSales = JSON.parse(localStorage.getItem("offlineSales")) || [];
      saleData.tempId = Date.now();
      offlineSales.push(saleData);
      localStorage.setItem("offlineSales", JSON.stringify(offlineSales));
    } catch (error) {
      console.error("Error guardando venta offline:", error);
    }
  };

  // Sincronizar ventas pendientes
  const syncOfflineSales = async () => {
    try {
      let offlineSales = JSON.parse(localStorage.getItem("offlineSales")) || [];
      if (offlineSales.length === 0) return;
      const token = localStorage.getItem("token");
      const updatedSales = [];
      for (let sale of offlineSales) {
        try {
          await customFetch(`${API_URL}/api/sales`, {
            method: "POST",
            body: JSON.stringify(sale),
          });
          console.log(`Venta tempId=${sale.tempId} sincronizada correctamente.`);
        } catch (err) {
          if (err.status === 401) {
            alert("La sincronización automática falló porque tu sesión expiró. Por favor, inicia sesión nuevamente.");
            localStorage.removeItem("token");
            localStorage.removeItem("role");
            localStorage.removeItem("isAdmin");
            navigate("/login");
            return;
          } else {
            console.warn(`Error al sincronizar venta tempId=${sale.tempId}`, err);
            updatedSales.push(sale);
          }
        }
      }
      localStorage.setItem("offlineSales", JSON.stringify(updatedSales));
      updatePendingSalesCount();
    } catch (error) {
      console.error("Error sincronizando ventas offline:", error);
    }
  };

  const handleOverlayClick = (e) => {
    if (e.target.classList.contains("popup-overlay")) {
      closePopup();
    }
  };

  return (
    <div className="app-container">
      {isAdmin && (
        <div className="dashboard-sidebar" onClick={() => navigate("/admin-options")}>
          <ArrowLeft size={40} className="back-icon" />
        </div>
      )}

      <div className="content-wrapper">
        <div className="main-content">
          <h2>Selección de Productos</h2>
          {loading ? (
            <p>Cargando productos...</p>
          ) : products.length === 0 ? (
            <p>No hay productos disponibles.</p>
          ) : (
            <div className="products-grid">
              {products.map((product, index) => (
                <div key={product.id} className="product-card" onClick={() => addToCart(product)}>
                  <div className="image-container">
                    {!product.imageError ? (
                      <img
                        src={product.image}
                        alt={product.name}
                        onError={() => {
                          setProducts((prevProducts) => {
                            const newProducts = [...prevProducts];
                            newProducts[index] = { ...product, imageError: true };
                            return newProducts;
                          });
                        }}
                      />
                    ) : (
                      <div className="image-placeholder">Imagen no disponible</div>
                    )}
                  </div>
                  <div className="product-info">
                    <h3>{product.name}</h3>
                    <p>${product.price}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="cart-panel">
          <h2>Carrito</h2>
          <div className="cart-list">
            {cart.map((item) => (
              <div key={item.id} className="cart-item">
                <div className="cart-item-text">
                  {item.name} x {item.quantity}
                </div>
                <button className="delete-button" onClick={() => removeFromCart(item.id)}>
                  <Trash2 size={18} />
                </button>
              </div>
            ))}
          </div>
          <div className="cart-footer">
            <span className="total-amount">Total: ${total}</span>
            <button className="accept-sale" onClick={handlePayment} disabled={!isCashRegisterOpen}>
              Aceptar Venta
            </button>
            {!isCashRegisterOpen && (
              <p className="cash-register-closed">
                ⚠️ La caja está cerrada. No se pueden realizar ventas.
              </p>
            )}
            {pendingSalesCount > 0 && (
              <button className="sync-sales-btn" onClick={syncOfflineSales} disabled={offline}>
                Sincronizar {pendingSalesCount} ventas pendientes
              </button>
            )}
            {offlineMessage && <div className="offline-message">{offlineMessage}</div>}
            {lastSale && (
              <button className="undo-sale-btn" onClick={undoLastSale}>
                Deshacer Última Venta
              </button>
            )}
          </div>
        </div>
      </div>

      {!isAdmin && (
        <div className="logout-button-container">
          <div className="logout-button" onClick={handleLogout}>
            🚪
          </div>
        </div>
      )}

      {showPopup && (
        <div className="popup-overlay" onClick={handleOverlayClick}>
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={closePopup} />
            {showQR ? (
              <div className="qr-popup-container">
                <h2 className="qr-popup-title">Escanea el código QR para pagar</h2>
                <PaymentQR amount={total} />
                <button className="back-button" onClick={() => setShowQR(false)}>
                  Volver
                </button>
              </div>
            ) : (
              <>
                <h2>Selecciona el Método de Pago</h2>
                <div className="popup-buttons">
                  <button className="popup-btn popup-btn-cash" onClick={handleCashPayment}>
                    💸 Pagar con Efectivo
                  </button>
                  <button className="popup-btn popup-btn-qr" onClick={() => {
                      console.log("Pago con QR seleccionado");
                      setShowQR(true);
                    }}>
                    📱 Pagar con QR
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {showOfflinePopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={() => setShowOfflinePopup(false)} />
            <h2>Sin conexión</h2>
            <p>
              Te encuentras sin conexión a Internet. Las ventas que realices se guardarán localmente y se sincronizarán cuando vuelvas a estar online.
            </p>
            <button className="popup-btn popup-btn-qr" onClick={() => setShowOfflinePopup(false)}>
              Aceptar
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
