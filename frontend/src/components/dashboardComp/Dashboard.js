// src/components/dashboard/Dashboard.js
import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { StompSubscription } from '@stomp/stompjs';
import "./dashboardStyle.css";
import { ArrowLeft, Trash2, X } from "lucide-react";
import { customFetch } from "../../utils/api";
import PaymentQR from "../paymentqr/PaymentQR";
import { API_URL, WS_URL } from "../../config/apiConfig";
import PrintButton from '../impression/PrintButton';

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

  // Estados para el popUp de deshacer
  const [showUndoPopup, setShowUndoPopup] = useState(false);
  const [saleToUndo, setSaleToUndo] = useState(null);

  // Estados para modo offline
  const [offline, setOffline] = useState(!navigator.onLine);
  const [showOfflinePopup, setShowOfflinePopup] = useState(false);
  const [pendingSalesCount, setPendingSalesCount] = useState(0);
  const [offlineMessage, setOfflineMessage] = useState("");
  // Estado para almacenar la última venta exitosa (online)
  const [lastSale, setLastSale] = useState(null);
  // Estado para recibir el estado del pago vía WebSocket
  const [paymentStatus, setPaymentStatus] = useState("");

  const [categories, setCategories] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState(null);

  const [showEmptyCartPopup, setShowEmptyCartPopup] = useState(false);

  const [customizingProduct, setCustomizingProduct] = useState(null);
  const [tempIngredients, setTempIngredients] = useState([]);

  const cartRef = useRef(cart);
  const totalRef = useRef(total);

  useEffect(() => {
    cartRef.current = cart;
    totalRef.current = total;
  }, [cart, total]);

  // Suscripción a WebSocket para recibir notificaciones de pago
  useEffect(() => {
    if (offline) {
      console.log("Offline: no inicializo STOMP/WebSocket");
      return;
    }
    console.log("Iniciando STOMP sobre WebSocket a:", WS_URL);
    const brokerURL = WS_URL.replace(/^http/, "ws") + "/ws";
    const client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      debug: (str) => console.log("STOMP DEBUG:", str),
    });

    client.onConnect = () => {
      client.subscribe(
        "/topic/payment-status",
        async (msg) => {
          const raw = msg.body.toLowerCase();
          if (!["approved", "rejected"].includes(raw)) return;
          const translated = raw === "approved" ? "Aprobado" : "Rechazado";
          setPaymentStatus(translated);

          if (raw === "approved") {
            const saleData = {
              totalAmount: totalRef.current,
              paymentMethod: "QR",
              dateTime: new Date().toISOString(),
              items: cartRef.current.map(item => ({
                productId: item.id,
                quantity: item.quantity,
                unitPrice: item.price,
              }))
            };

            try {
              const response = await customFetch(
                `${API_URL}/api/sales`,
                {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify(saleData)
                }
              );
              setLastSale(response);
              setCart([]);
              setTotal(0);
              setShowPopup(false);
              setShowQR(false);
            } catch (err) {
              console.error("Error al guardar venta QR:", err);
              alert("Ocurrió un error guardando la venta QR.");
            }
          }
        },
        { id: "payment-status-sub" }
      );
    };

    client.onStompError = frame => console.error("Error STOMP:", frame.body);
    client.activate();
    return () => client.deactivate();
  }, [offline]);


  // Si se recibe un estado de pago, se oculta automáticamente después de 5 segundos
  useEffect(() => {
    if (paymentStatus) {
      const timer = setTimeout(() => setPaymentStatus(""), 5000);
      return () => clearTimeout(timer);
    }
  }, [paymentStatus]);

  const openCustomize = prod => {
    setCustomizingProduct(prod);
    // marcamos todos por defecto
    setTempIngredients(prod.ingredients.map(i => i.id));
  };

  // Verificar si la caja está abierta
  const checkCashRegisterStatus = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/cash-register/status`);
      setIsCashRegisterOpen(response);
    } catch (error) {
      console.error("Error al verificar la caja:", error);
    }
  };

  // Actualizar el contador de ventas pendientes (offline)
  const updatePendingSalesCount = () => {
    const offlineSales = JSON.parse(localStorage.getItem("offlineSales")) || [];
    setPendingSalesCount(offlineSales.length);
  };

  useEffect(() => {
    const role = localStorage.getItem("role");
    setIsAdmin(role === "ADMIN");
    fetchProducts();
    fetchCategories();
    checkCashRegisterStatus();
    updatePendingSalesCount();
  }, []);

  const fetchCategories = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/categories`);
      setCategories(Array.isArray(response) ? response : []);
    } catch (error) {
      console.error("Error al obtener categorías:", error);
    }
  };

  // Manejo de eventos online/offline
  useEffect(() => {
    const handleOnline = () => {
      console.log("Conexión recuperada.");
      setOffline(false);             // reactivará el hook anterior
      setShowOfflinePopup(false);
      setOfflineMessage("");
      syncOfflineSales();            // sincroniza ventas pendientes
    };
    const handleOffline = () => {
      console.log("Sin conexión.");
      setOffline(true);
      setShowOfflinePopup(true);
    };

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  const fetchProducts = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/products`);
      if (!Array.isArray(response)) throw new Error("La respuesta no es un array");
      const productsWithFixedImages = response.map((product) => ({
        ...product,
        image:
          product.image && product.image.startsWith("data:image")
            ? product.image
            : `data:image/png;base64,${product.image}`,
        imageError: false,
        ingredients: Array.isArray(product.ingredients) ? product.ingredients : [],
      }));
      setProducts(productsWithFixedImages);
    } catch (error) {
      console.error("Error al obtener productos:", error);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  const sameIngredientSet = (a = [], b = []) => {
    if (a.length !== b.length) return false;
    const sa = [...a].sort(), sb = [...b].sort();
    return sa.every((v, i) => v === sb[i]);
  };

  const addToCart = (product) => {
    setCart(prevCart => {
      // extraemos sólo los IDs de ingredientes de la variante que queremos añadir
      const thisIds = (product.ingredients || []).map(i => i.id);

      // buscamos en el carrito una línea con mismo producto Y mismos ingredientes
      const idx = prevCart.findIndex(item => {
        if (item.id !== product.id) return false;
        const itemIds = (item.ingredients || []).map(i => i.id);
        return sameIngredientSet(itemIds, thisIds);
      });

      if (idx !== -1) {
        // ya existe esa variante, aumentamos quantity
        const updated = [...prevCart];
        updated[idx] = {
          ...updated[idx],
          quantity: updated[idx].quantity + 1
        };
        return updated;
      } else {
        // nueva variante → la añadimos con quantity 1
        return [...prevCart, { ...product, quantity: 1 }];
      }
    });

    // actualizamos el total normalmente
    setTotal(prev => prev + product.price);
  };

  const removeFromCart = (lineIndex) => {
    setCart(prev => {
      const copy = [...prev];
      copy.splice(lineIndex, 1);
      setTotal(copy.reduce((sum, it) => sum + it.price * it.quantity, 0));
      return copy;
    });
  };

  const handleLogout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("role");
    localStorage.removeItem("isAdmin");
    navigate("/login");
  };

  // Lógica para abrir el popup de pago
  const handlePayment = () => {
    if (cart.length === 0) {
      setShowEmptyCartPopup(true);
      return;
    }
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
        ingredientIds: item.ingredients.map(i => i.id)
      })),
    };

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

  const undoLastSale = () => {
    if (!lastSale) {
      alert("No hay ventas para deshacer.");
      return;
    }
    setSaleToUndo(lastSale);      // Guardás qué venta querés cancelar
    setShowUndoPopup(true);       // Mostrás el popup de confirmación
  };

  // Paso 2: Confirmar la cancelación solo si el usuario acepta
  const confirmUndoSale = async () => {
    try {
      await customFetch(`${API_URL}/api/statistics/cancel-sale/${saleToUndo.id}`, {
        method: "PUT",
      });
      setLastSale(null);
      setSaleToUndo(null);
      setShowUndoPopup(false);
    } catch (error) {
      console.error("Error al deshacer la venta:", error);
      alert("No se pudo deshacer la última venta.");
    }
  };

  // Almacenar venta offline en localStorage
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

  // Sincronizar ventas offline
  const syncOfflineSales = async () => {
    try {
      let offlineSales = JSON.parse(localStorage.getItem("offlineSales")) || [];
      if (offlineSales.length === 0) return;
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

  // Función para renderizar el estado del pago recibido vía WebSocket
  const renderPaymentStatus = () =>
    paymentStatus && (
      <div
        className="payment-status-message"
        style={{
          backgroundColor:
            paymentStatus.toLowerCase() === "aprobado" ? "#4caf50" : "#f44336",
        }}
        onClick={() => setPaymentStatus("")}
      >
        <p>Estado del pago: {paymentStatus}</p>
      </div>
    );

    const exampleOrder = {
      id: 'ABC-123',
      totalAmount: '199'
    };

  return (
    <div className="app-container">
      {isAdmin && (
        <div className="dashboard-sidebar" onClick={() => navigate("/admin-options")}>
          <ArrowLeft size={40} className="back-icon" />
        </div>
      )}

      {renderPaymentStatus()}

      <div className="content-wrapper">
        <div className="main-content">
          <h2>Selección de Productos</h2>

          <div className="category-tabs-dashboard">
            <button
              className={!selectedCategory ? "active-tab-dashboard" : ""}
              onClick={() => setSelectedCategory(null)}
            >
              Todos
            </button>
            {categories.map((cat) => (
              <button
                key={cat.id}
                className={selectedCategory === cat.id ? "active-tab-dashboard" : ""}
                onClick={() => setSelectedCategory(cat.id)}
              >
                {cat.name}
              </button>
            ))}
          </div>

          {loading ? (
            <p>Cargando productos...</p>
          ) : products.length === 0 ? (
            <p>No hay productos disponibles.</p>
          ) : (
            <div className="products-grid">
              {products
                .filter((product) => !selectedCategory || product.categoryId === selectedCategory)
                .map((product, index) => (
                  <div
                    key={product.id}
                    className="product-card"
                    onClick={() => {
                      const cat = categories.find(c => c.id === product.categoryId)
                      if (cat?.hasIngredients) {
                        openCustomize(product);
                      } else {
                        addToCart(product);
                      }
                    }}
                  >
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
                    <h3><span className="product-name">{product.name}</span></h3>
                      <p>${product.price}</p>
                    </div>
                  </div>
                ))}
            </div>
          )}
        </div>
        <div style={{ padding: 20 }}>
      <h1>Pedidos</h1>
      <p>Pedido: {exampleOrder.id}</p>
      <p>Cliente: {exampleOrder.customerName}</p>
      <PrintButton order={exampleOrder} />
    </div>
        <div className="cart-panel">
          <h2>Carrito</h2>
          <div className="cart-list">
            {cart.map((item, idx) => {
              // 1) Buscamos el producto original para obtener su lista completa de ingredientes
              const original = products.find((p) => p.id === item.id) || { ingredients: [] };
              const originalIds = original.ingredients.map((ing) => ing.id);

              // 2) Filtramos los ingredientes que ya NO están en item.ingredients
              const removedNames = original.ingredients
                .filter((orig) => !item.ingredients.some((i) => i.id === orig.id))
                .map((i) => i.name);

              return (
                <div key={`${item.id}-${idx}`} className="cart-item">
                  <div className="cart-item-text">
                  <span className="product-name">{item.name}</span>
                    {/* 3) Si hay ingredientes quitados, los mostramos */}
                    {removedNames.length > 0 && ` – sin ${removedNames.join(", ")} `}
                    <span className="product-quantity">{" "}x{item.quantity}</span>
                  </div>
                  <button
                    className="delete-button"
                    onClick={() => removeFromCart(idx)}
                  >
                    <Trash2 size={18} />
                  </button>
                </div>
              );
            })}
          </div>
          <div className="cart-footer">
            <span className="total-amount">Total: ${total}</span>
            <button
              className="accept-sale"
              onClick={handlePayment}
              disabled={!isCashRegisterOpen}
            >
              Aceptar Venta
            </button>
            {!isCashRegisterOpen && (
              <p className="cash-register-closed">
                ⚠️ La caja está cerrada. No se pueden realizar ventas.
              </p>
            )}
            {pendingSalesCount > 0 && (
              <button
                className="sync-sales-btn"
                onClick={syncOfflineSales}
                disabled={offline}
              >
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
                  <button className="popup-btn" onClick={handleCashPayment}>
                    💸 Pagar con Efectivo
                  </button>
                  <button
                    className="popup-btn popup-btn-qr"

                    onClick={() => {
                      console.log("Pago con QR seleccionado");
                      setShowQR(true);
                    }}
                    disabled={offline}
                    title={offline ? "Necesitas conexión para QR" : ""}
                  >
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
      {showUndoPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={() => setShowUndoPopup(false)} />
            <h2>¿Seguro que quieres cancelar esta venta?</h2>
            <p>Monto: ${saleToUndo?.totalAmount}</p>
            <p>Fecha: {new Date(saleToUndo?.dateTime).toLocaleString()}</p>
            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={confirmUndoSale}>
                ✅ Confirmar
              </button>
              <button className="popup-btn popup-btn-qr" onClick={() => setShowUndoPopup(false)}>
                ❌ Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
      {showEmptyCartPopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={32}
              onClick={() => setShowEmptyCartPopup(false)}
            />
            <h2>Carrito vacío</h2>
            <p>Debes agregar al menos un producto al carrito antes de realizar la venta.</p>
            <button
              className="popup-btn popup-btn-empty"
              onClick={() => setShowEmptyCartPopup(false)}
            >
              Aceptar
            </button>
          </div>
        </div>
      )}

      {customizingProduct && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" onClick={() => setCustomizingProduct(null)} />
            <h2>{customizingProduct.name} – Quita Ingredientes</h2>
            <div className="ingredient-list">
              {customizingProduct.ingredients.map(ing => (
                <label key={ing.id} className="ingredient-item">
                  <input
                    type="checkbox"
                    checked={tempIngredients.includes(ing.id)}
                    onChange={() => {
                      setTempIngredients(t =>
                        t.includes(ing.id) ? t.filter(x => x !== ing.id) : [...t, ing.id]
                      );
                    }}
                  />
                  {ing.name}
                </label>
              ))}
            </div>
            <button
              className="popup-btn popup-btn-cash"
              onClick={() => {
                // aquí construimos un “item” modificado:
                addToCart({
                  ...customizingProduct,
                  ingredients: customizingProduct.ingredients.filter(i =>
                    tempIngredients.includes(i.id)
                  )
                });
                setCustomizingProduct(null);
              }}
            >
              Agregar al Carrito
            </button>
          </div>
        </div>
      )}
    </div>

  );
};

export default Dashboard;
