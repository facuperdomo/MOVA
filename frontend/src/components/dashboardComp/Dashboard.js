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
import {
  printOrder,
  printItemsReceipt
} from "../../utils/print";
import PaymentStatusNotifier from '../paymentqr/PaymentStatusNotifier';
import AddMesaModal from "../addMesaModal/AddMesaModal";
import PaymentOptionsModal from "../account/PaymentOptionsModal";
import AccountsListModal from "../account/AccountsListModal";
import AccountPaymentsModal from "../account/AccountPaymentsModal";

const Dashboard = () => {
  const navigate = useNavigate();
  const [branchId, setBranchId] = useState(null);
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

  const [isPrinting, setIsPrinting] = useState(false);
  const [printError, setPrintError] = useState(false);

  const [enableIngredients, setEnableIngredients] = useState(false);

  const [accounts, setAccounts] = useState([]);
  const [newAccountName, setNewAccountName] = useState('');
  const [selectedAccountId, setSelectedAccountId] = useState(null);
  const [showAccountsModal, setShowAccountsModal] = useState(false);

  const [showDetailModal, setShowDetailModal] = useState(false);
  const [detailAccount, setDetailAccount] = useState(null);
  const [showAddModal, setShowAddModal] = useState(false);

  const [accountItems, setAccountItems] = useState([]);

  const [showPaymentModal, setShowPaymentModal] = useState(false);

  const [splitTotal, setSplitTotal] = useState(0);
  const [splitRemaining, setSplitRemaining] = useState(0);
  const [paidMoney, setPaidMoney] = useState(0);
  const [currentTotal, setCurrentTotal] = useState(0);

  const [itemPayments, setItemPayments] = useState([]);
  const [showPaymentsModal, setShowPaymentsModal] = useState(false);
  const [paymentsAccountId, setPaymentsAccountId] = useState(null);

  const cartRef = useRef(cart);
  const totalRef = useRef(total);

  useEffect(() => {
    cartRef.current = cart;
    totalRef.current = total;
  }, [cart, total]);

  useEffect(() => {
    const storedBranchId = localStorage.getItem("branchId");

    if (!storedBranchId) return;
    setBranchId(parseInt(storedBranchId));
    (async () => {
      try {
        const branch = await customFetch(`${API_URL}/api/branches/${storedBranchId}`);
        console.log("flag enableIngredients:", branch.enableIngredients);
        setEnableIngredients(branch.enableIngredients);
      } catch (err) {
        console.error("Error al cargar configuración de sucursal:", err);
      }
    })();
    loadAccounts(storedBranchId);
  }, []);

  // Suscripción a WebSocket para recibir notificaciones de pago
  useEffect(() => {
    if (offline) {
      console.log("Offline: no inicializo STOMP/SockJS");
      return;
    }
    console.log("Iniciando STOMP/SockJS a:", WS_URL);

    const client = new Client({
      // 1) Creamos la conexión SockJS
      webSocketFactory: () => new SockJS(`${WS_URL}/ws-sockjs`),
      reconnectDelay: 5000,
      debug: (str) => console.log("STOMP/SockJS DEBUG:", str),
    });

    client.onConnect = () => {
      // 2) Nos suscribimos al topic de estado de pago
      client.subscribe("/topic/payment-status", async (msg) => {
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
              ingredientIds: item.ingredients?.map(i => i.id) ?? []
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

            setIsPrinting(true);
            setPrintError(false);
            try {
              await printOrder(response);
            } catch (err) {
              console.error("❌ Falló impresión automática tras QR:", err);
              setPrintError(true);
            } finally {
              setIsPrinting(false);
            }
            setShowQR(false);
            setShowPopup(false);
          } catch (err) {
            console.error("Error al guardar venta QR:", err);
            alert("Ocurrió un error guardando la venta QR.");
          }
        }
      }, { id: "payment-status-sub" });
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

  useEffect(() => {
    if (selectedAccountId) loadAccountItems(selectedAccountId);
    else setAccountItems([]);
  }, [selectedAccountId, products]);

  const handleAcceptSale = () => {
    console.log("👉 handleAcceptSale fired", { selectedAccountId });
    if (selectedAccountId) {
      openPaymentModal();
    } else {
      handlePayment();
    }
  };

  const loadAccountItems = async (accountId) => {
    try {
      const account = await customFetch(`${API_URL}/api/accounts/${accountId}`);
      console.log("🔍 Raw account payload:", account);
      console.log("🔍 Raw items array:", account.items);
      const normalized = account.items.map(line => {
        const prod = products.find(p => p.id === line.productId) || { name: '', ingredients: [], price: 0 };
        return {
          // AHORA usamos `id` igual que en el carrito local
          id: line.id,
          productId: line.productId,
          productName: prod.name,
          price: prod.price || 0,
          quantity: line.quantity,
          ingredients: (line.ingredientIds || []).map(ingId => {
            const ing = prod.ingredients.find(x => x.id === ingId);
            return { id: ingId, name: ing?.name ?? '' };
          }),
          paid: line.paid
        };
      });
      setAccountItems(normalized);
    } catch (err) {
      console.error("[ERROR] loadAccountItems:", err);
    }
  };

  const openDetailModal = (acc) => {
    setDetailAccount(acc);
    setShowDetailModal(true);
  };

  const closeDetailModal = () => {
    setDetailAccount(null);
    setShowDetailModal(false);
  };

  const handleCreate = async () => {
    await createAccount();
    setShowAddModal(false);
  };

  const openPaymentModal = async () => {
    // 0) recarga items
    await loadAccountItems(selectedAccountId);

    // 2) traete también el split/status —> itemPayments
    const status = await customFetch(
      `${API_URL}/api/accounts/${selectedAccountId}/split/status`
    );
    setItemPayments(status.itemPayments || []);

    // 1.a) si nunca se inicializó split, lo inicializo con 1
    if (status.total === 0) {
      await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/split?people=1`,
        { method: "PUT" }
      );
      status = await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/split/status`
      );
    }

    setSplitTotal(status.total);
    setSplitRemaining(status.remaining);
    setPaidMoney(status.paidMoney);
    setCurrentTotal(status.currentTotal);
    setItemPayments(status.itemPayments || []);

    // 2) abro el modal
    setShowPaymentModal(true);
  };


  const loadAccounts = async (branchId) => {
    try {
      // 1) Traemos la lista básica de cuentas abiertas
      const data = await customFetch(
        `${API_URL}/api/accounts?branchId=${branchId}&closed=false`
      );

      // 2) Para cada cuenta, consultamos su estado de “split” para saber cuánto falta por pagar
      const enriched = await Promise.all(
        data.map(async (acc) => {
          // a) Mapeamos los items como antes
          const items = acc.items.map(line => {
            const prod = products.find(p => p.id === line.productId) || {};
            return {
              accountItemId: line.id,
              productId: line.productId,
              productName: prod.name || line.productName || 'Producto',
              quantity: line.quantity
            };
          });

          // b) Obtenemos el estado de split para esta cuenta
          let remainingMoney = 0;
          try {
            const status = await customFetch(
              `${API_URL}/api/accounts/${acc.id}/split/status`
            );
            // status.currentTotal = total de la cuenta, status.paidMoney = cuánto ya se pagó
            remainingMoney = status.currentTotal - (status.paidMoney || 0);
          } catch (err) {
            console.error(`Error al obtener split/status de cuenta ${acc.id}:`, err);
          }

          // c) Devolvemos el objeto “acc” enriquecido con items y remainingMoney
          return {
            ...acc,
            items,
            remainingMoney
          };
        })
      );

      // 3) Guardamos en el estado
      setAccounts(enriched);
    } catch (err) {
      console.error("Error al cargar cuentas:", err);
    }
  };

  // 2) Crea una cuenta y la añade al estado
  const createAccount = async () => {
    if (!newAccountName.trim()) return;
    try {
      // customFetch retorna ya el objeto creado
      const createdAccount = await customFetch(`${API_URL}/api/accounts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ branchId, name: newAccountName })
      });
      setNewAccountName("");
      // añado la nueva cuenta al array para que aparezca inmediatamente
      setAccounts(prev => [...prev, createdAccount]);
    } catch (err) {
      console.error("Error creando cuenta:", err);
    }
  };

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

  /**
 * Elimina una línea de cuenta por su ID interno (accountItemId)
 */
  const removeAccountItem = async (id) => {
    console.log("Borrando línea con id =", id);
    try {
      // 1) Primero, eliminamos el ítem en el backend:
      await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/items/${id}`,
        { method: "DELETE" }
      );

      // 2) Re-cargamos la lista de ítems de la cuenta:
      await loadAccountItems(selectedAccountId);

      // 3) Consultamos nuevamente el estado del split:
      const status = await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/split/status`
      );

      // 4) Calculamos cuánto dinero queda por pagar:
      const remainingMoney = status.currentTotal - (status.paidMoney || 0);

      // 5) Si no queda nada pendiente, cerramos la cuenta automáticamente:
      if (remainingMoney <= 0) {
        await customFetch(`${API_URL}/api/accounts/${selectedAccountId}/close`, {
          method: "PUT",
        });
        // 6) Limpiamos el estado de cuenta seleccionada y recargamos la lista general:
        setSelectedAccountId(null);
        await loadAccounts(branchId);
      }
    } catch (err) {
      console.error("Error eliminando item de cuenta:", err);
      alert("No se pudo eliminar el producto de la cuenta.");
    }
  };

  const decrementAccountItem = async (item) => {
    try {
      if (item.quantity > 1) {
        // 1) Si tenía más de 1 unidad, hacemos PUT para restar 1:
        await customFetch(
          `${API_URL}/api/accounts/${selectedAccountId}/items/${item.id}`,
          {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ quantity: item.quantity - 1 }),
          }
        );
      } else {
        // 2) Si solo quedaba 1 unidad, lo eliminamos por completo:
        await customFetch(
          `${API_URL}/api/accounts/${selectedAccountId}/items/${item.id}`,
          { method: "DELETE" }
        );
      }

      // 3) Re-cargamos la lista de ítems de la cuenta:
      await loadAccountItems(selectedAccountId);

      // 4) Consultamos nuevamente el estado del split:
      const status = await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/split/status`
      );

      // 5) Calculamos cuánto dinero queda por pagar:
      const remainingMoney = status.currentTotal - (status.paidMoney || 0);

      // 6) Si ya no queda nada por pagar, cerramos la cuenta:
      if (remainingMoney <= 0) {
        await customFetch(`${API_URL}/api/accounts/${selectedAccountId}/close`, {
          method: "PUT",
        });
        setSelectedAccountId(null);
        await loadAccounts(branchId);
      }
    } catch (err) {
      console.error("Error decrementando ítem de cuenta:", err);
      alert("No se pudo actualizar la cuenta.");
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
      // <-- aquí sí response existe:
      console.log('📦 productos crudos del back:', response);

      const prods = response.map(p => ({
        ...p,
        image: p.image.startsWith('data:image')
          ? p.image
          : `data:image/png;base64,${p.image}`,
        imageError: false,
        ingredients: Array.isArray(p.ingredients) ? p.ingredients : []
      }));

      setProducts(prods);
    } catch (err) {
      console.error('Error al fetchProducts:', err);
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
      // 1) IDs de ingredientes de la variante que quiero añadir
      const thisIds = (product.ingredients || []).map(i => i.id);

      // 2) Buscar en el carrito un item con mismo producto Y mismos ingredientes
      const idx = prevCart.findIndex(item => {
        if (item.id !== product.id) return false;
        // IDs del item ya en carrito
        const itemIds = (item.ingredients || []).map(i => i.id);
        // compara arreglos ignorando orden
        return sameIngredientSet(itemIds, thisIds);
      });

      if (idx !== -1) {
        // Ya existe esa variante, aumentamos quantity
        const updated = [...prevCart];
        updated[idx] = {
          ...updated[idx],
          quantity: updated[idx].quantity + 1
        };
        return updated;
      }

      // No existe → nueva línea con quantity 1
      return [...prevCart, { ...product, quantity: 1 }];
    });

    // 3) Actualizamos el total
    setTotal(prev => prev + product.price);
  };

  const handleProductClick = async (product) => {
    if (enableIngredients && product.ingredients?.length > 0) {
      openCustomize(product);
      return;
    }

    if (!selectedAccountId) {
      addToCart(product);
      return;
    }

    // → Aquí la magia:
    try {
      // 1) Buscamos en accountItems si ya hay un item para este productId
      const existing = accountItems.find(it => it.productId === product.id);
      if (existing) {
        // 2a) Si existe, hacemos PUT para subir +1
        await customFetch(
          `${API_URL}/api/accounts/${selectedAccountId}/items/${existing.id}`,
          {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ quantity: existing.quantity + 1 })
          }
        );
      } else {
        // 2b) Si no existe, hacemos POST para crear uno nuevo con qty=1
        await customFetch(
          `${API_URL}/api/accounts/${selectedAccountId}/items`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ productId: product.id, quantity: 1 })
          }
        );
      }

      // 3) Refrescamos la lista siempre
      await loadAccountItems(selectedAccountId);
    } catch (err) {
      console.error("Error agregando a cuenta:", err);
      alert("No se pudo actualizar la cuenta.");
    }
  };

  const removeFromCart = (lineIndex) => {
    setCart(prev => {
      const copy = [...prev];
      copy.splice(lineIndex, 1);
      setTotal(copy.reduce((sum, it) => sum + it.price * it.quantity, 0));
      return copy;
    });
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
      const savedSale = await customFetch(`${API_URL}/api/sales`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(saleData),
      });

      // 1) Limpiar UI tras guardado
      setLastSale(savedSale);
      setCart([]);
      setTotal(0);
      setShowPopup(false);
      setOfflineMessage("");

      // 2) Disparar impresión
      setIsPrinting(true);
      setPrintError(false);
      try {
        await printOrder(savedSale);
      } catch (err) {
        console.error("❌ Falló impresión automática:", err);
        setPrintError(true);
      } finally {
        setIsPrinting(false);
      }
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

  const handleRetryPrint = async () => {
    if (!lastSale) return;
    setIsPrinting(true);
    setPrintError(false);
    try {
      await printOrder(lastSale);
    } catch (err) {
      console.error("❌ Reintento de impresión fallido:", err);
      setPrintError(true);
    } finally {
      setIsPrinting(false);
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

  const groupItems = (items) => {
    const map = {};
    items.forEach(item => {
      // si viene de accountItems usamos el productId, si viene del carrito usamos el id
      const prodId = item.productId != null ? item.productId : item.id;
      // construimos la clave también con los ingredientes para distinguir variantes
      const ingredientsKey = (item.ingredients || [])
        .map(i => i.id)
        .sort()
        .join(",");
      const key = `${prodId}-${ingredientsKey}`;

      if (map[key]) {
        map[key].quantity += item.quantity;
      } else {
        // hacemos un shallow copy para no mutar el original
        map[key] = { ...item };
      }
    });
    return Object.values(map);
  };


  const accountTotal = groupItems(accountItems)
    .reduce((sum, line) => {
      const p = products.find(p => p.id === line.productId);
      return sum + (p?.price || 0) * line.quantity;
    }, 0);

  /**
* Se llama cuando el usuario paga TODO y elige "Sí, cerrar cuenta".
* Aquí el backend ya habrá cerrado la cuenta, por lo que solo limpiamos estados y recargamos cuentas.
*/
  const handlePaidAndClose = async () => {
    try {
      // 1) Limpiamos la cuenta seleccionada:
      setSelectedAccountId(null);
      // 2) Volvemos a cargar la lista de cuentas abiertas:
      await loadAccounts(branchId);
      // 3) Reiniciamos estados relacionados al split:
      setSplitTotal(0);
      setSplitRemaining(0);
      setPaidMoney(0);
      setCurrentTotal(0);
      setItemPayments([]);
    } catch (err) {
      console.error("Error actualizando dashboard tras cerrar cuenta:", err);
    }
  };

  /**
   * Se llama cuando el usuario paga TODO pero elige "No, dejarla abierta".
   * En ese caso solo recargamos el estado del split (para forzar splitRemaining = 0),
   * pero la cuenta sigue abierta.
   */
  const handlePaidWithoutClose = async () => {
    try {
      // 1) recarga los items desde el backend
      await loadAccountItems(selectedAccountId);

      // 2) ahora sí actualiza el estado de split
      const status = await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/split/status`
      );
      setSplitTotal(status.total);
      setSplitRemaining(status.remaining);
      setPaidMoney(status.paidMoney);
      setCurrentTotal(status.currentTotal);
      setItemPayments(status.itemPayments || []);
    } catch (err) {
      console.error("Error actualizando split tras pago completo sin cerrar:", err);
    }
  };


  const handlePartialPaid = async () => {
    // 1) Reducimos en 1 el número de porciones restantes localmente:
    setSplitRemaining((r) => r - 1);

    try {
      // 2) Consultamos nuevamente el estado completo del split:
      const status = await customFetch(
        `${API_URL}/api/accounts/${selectedAccountId}/split/status`
      );

      // 3) Calculamos cuánto dinero queda por pagar:
      const remainingMoney = status.currentTotal - (status.paidMoney || 0);

      // 4) Si no queda nada pendiente, intentamos cerrar la cuenta:
      if (remainingMoney <= 0) {
        try {
          await customFetch(
            `${API_URL}/api/accounts/${selectedAccountId}/close`,
            { method: "PUT" }
          );
        } catch (err) {
          // Si el servidor responde 400 significa “la cuenta ya estaba cerrada”,
          // así que lo ignoramos en ese caso:
          if (err.status === 400) {
            console.warn("Cuenta ya estaba cerrada");
          } else {
            throw err;
          }
        }

        // 5) Limpiamos la cuenta seleccionada y recargamos la lista de cuentas:
        setSelectedAccountId(null);
        await loadAccounts(branchId);

        // 6) Aseguramos que splitRemaining quede a cero
        setSplitRemaining(0);
      }
    } catch (err) {
      console.error("❌ No se pudo cerrar la cuenta:", err);
      alert("Error cerrando la cuenta");
    }
  };

  const rawItems = selectedAccountId ? accountItems : cart;
  const displayItems = groupItems(rawItems);
  const totalUnits = displayItems.reduce((sum, it) => sum + it.quantity, 0);

  const handleCloseAccount = async (accountId) => {
    try {
      // 1) Obtenemos estado de split para saber si falta algo por pagar
      const status = await customFetch(
        `${API_URL}/api/accounts/${accountId}/split/status`
      );
      const remainingMoney = status.currentTotal - (status.paidMoney || 0);

      if (remainingMoney <= 0) {
        // Si no falta nada, la cerramos directamente
        await customFetch(`${API_URL}/api/accounts/${accountId}/close`, {
          method: "PUT",
        });
        setSelectedAccountId(null);
        await loadAccounts(branchId);
      } else {
        // Si aún falta, abrimos el modal de pago para esa cuenta
        setSelectedAccountId(accountId);
        const stat = await customFetch(
          `${API_URL}/api/accounts/${accountId}/split/status`
        );
        setSplitTotal(stat.total);
        setSplitRemaining(stat.remaining);
        setPaidMoney(stat.paidMoney);
        setCurrentTotal(stat.currentTotal);
        setItemPayments(stat.itemPayments || []);
        setShowPaymentModal(true);
      }
    } catch (err) {
      console.error("Error al intentar cerrar la cuenta:", err);
      alert("No se pudo procesar el cierre de la cuenta.");
    }
  };

  // ——————————————————————————————————————————————————————————
  // ¿Pagar? Abre directamente el modal de opciones de pago para la cuenta indicada
  const handlePayAccount = async (accountId) => {
    try {
      setSelectedAccountId(accountId);
      // 1) Obtenemos estado de split
      const stat = await customFetch(
        `${API_URL}/api/accounts/${accountId}/split/status`
      );
      setSplitTotal(stat.total);
      setSplitRemaining(stat.remaining);
      setPaidMoney(stat.paidMoney);
      setCurrentTotal(stat.currentTotal);
      setItemPayments(stat.itemPayments || []);

      // 2) Abrimos el modal de PaymentOptionsModal
      setShowPaymentModal(true);
      // (Ya no se cierra el modal de cuentas, así que eliminamos la línea de abajo)
      // setShowAccountsModal(false);
    } catch (err) {
      console.error("Error abriendo modal de pago:", err);
      alert("No se pudo abrir el modal de pago.");
    }
  };

  const handlePrint = async ({ type, payload }) => {
    console.log("🚀 handlePrint recibido:", { type, payload });
    setIsPrinting(true);
    setPrintError(false);
    try {
      if (type === "PRODUCT_PAYMENT") {
        // Pago por producto → imprimimos SOLO esos items
        await printItemsReceipt(payload);
      } else {
        // Full closure o pagos parciales → imprimen con venta completa
        await printOrder(payload);
      }
    } catch (err) {
      console.error("Error imprimiendo ticket:", err);
      setPrintError(true);
    } finally {
      setIsPrinting(false);
    }
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
          {selectedAccountId && (() => {
            const acc = accounts.find(a => a.id === selectedAccountId);
            return (
              <div className="selected-account-banner">
                🧾 Cuenta seleccionada: {acc?.name}
                <button onClick={() => { setSelectedAccountId(null); setAccountItems([]); }} style={{ marginLeft: "1rem" }}>
                  ❌ Quitar cuenta
                </button>
              </div>
            );
          })()}
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
                    onClick={() => handleProductClick(product)}
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

        <div className="cart-panel">
          <h2>Carrito</h2>
          <div className="cart-list">
            {displayItems.map((item, idx) => {
              const key = selectedAccountId
                ? item.id
                : `${item.id}-${idx}`;
              const name = selectedAccountId
                ? item.productName
                : item.name;

              return (
                <div key={key} className="cart-item">
                  <div className="cart-item-text">
                    <span className="product-name">{name}</span>
                    <span className="product-quantity"> x{item.quantity}</span>
                  </div>
                  <button
                    className="delete-button"
                    onClick={() => {
                      if (selectedAccountId) {
                        decrementAccountItem(item);
                      } else {
                        removeFromCart(idx);
                      }
                    }}
                  >
                    <Trash2 size={18} />
                  </button>
                </div>
              );
            })}
          </div>
          <div className="cart-footer">
            <span className="total-amount">
              Total: ${selectedAccountId ? accountTotal.toFixed(2) : total.toFixed(2)}
            </span>
            <button
              className="accept-sale"
              onClick={handleAcceptSale}
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
              <div
                className="sale-actions"
                style={{
                  display: 'flex',
                  gap: '0.5rem',
                  alignItems: 'center',
                  marginTop: '1rem'
                }}
              >
                <button className="undo-sale-btn" onClick={undoLastSale}>
                  Deshacer Última Venta
                </button>

                {isPrinting ? (
                  <button className="popup-btn" disabled>
                    Imprimiendo…
                  </button>
                ) : printError ? (
                  <button className="popup-btn" onClick={handleRetryPrint}>
                    Reintentar impresión
                  </button>
                ) : (
                  // <-- aquí mostramos siempre un botón de impresión manual
                  <PrintButton order={lastSale} />
                )}
              </div>
            )}

          </div>
        </div>
      </div>

      {!isAdmin && (
        <div className="logout-button-container">
          <button className="account-button" onClick={() => {
            if (branchId) {
              loadAccounts(branchId);
              setShowAccountsModal(true);
            }
          }}>
            🧾
          </button>
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
      {showAccountsModal && (
        <AccountsListModal
          accounts={accounts}
          selectedAccountId={selectedAccountId}
          onClose={() => setShowAccountsModal(false)}

          onSelectAccount={(id) => {
            setSelectedAccountId(id);
            setAccountItems([]);
            loadAccountItems(id);
          }}

          onShowDetails={(acc) => {
            setDetailAccount(acc);
            setShowDetailModal(true);
          }}

          onShowPayments={(id) => {
            // “Ver Pagos” solo abre el modal de historial, NO el modal de pago
            setPaymentsAccountId(id);
            setShowPaymentsModal(true);
          }}

          onPayAccount={handlePayAccount}   // <--- NUEVO: botón “Pagar” abre PaymentOptionsModal

          onCreateNew={() => {
            setShowAddModal(true);
          }}

          onCloseAccount={handleCloseAccount} // botón “Cerrar cuenta”
        />
      )}
      <AddMesaModal
        open={showAddModal}
        onClose={() => setShowAddModal(false)}
        onCreate={handleCreate}
        newName={newAccountName}
        setNewName={setNewAccountName}
      />
      {showDetailModal && detailAccount && (
        <div className="popup-overlay table-detail-modal" onClick={e => {
          if (e.target.classList.contains("popup-overlay")) {
            closeDetailModal();
          }
        }}>
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={closeDetailModal} />
            <h2>{detailAccount.name} (#{detailAccount.id})</h2>
            <ul className="full-items">
              {detailAccount.items.map(i => (
                <li key={i.id}>{i.productName} x{i.quantity}</li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {showPaymentModal && selectedAccountId && (
        <PaymentOptionsModal
          accountId={selectedAccountId}
          items={accountItems}
          itemPayments={itemPayments}
          total={currentTotal}
          onClose={() => setShowPaymentModal(false)}
          // ——— Nuestras dos props nuevas ———
          onPaidAndClose={handlePaidAndClose}
          onPaidWithoutClose={handlePaidWithoutClose}
          // ————————————————————————————
          splitTotal={splitTotal}
          splitRemaining={splitRemaining}
          paidMoney={paidMoney}
          onSplitUpdate={(total, remaining, paidMoney, currentTotal, itemPayments) => {
            setSplitTotal(total);
            setSplitRemaining(remaining);
            setPaidMoney(paidMoney);
            setCurrentTotal(currentTotal);
            setItemPayments(itemPayments || []);
          }}
          onPrint={handlePrint}
        />
      )}
      {showPaymentsModal && paymentsAccountId && (
        <AccountPaymentsModal
          accountId={paymentsAccountId}
          onClose={() => {
            setShowPaymentsModal(false);
            setPaymentsAccountId(null);
          }}
        />
      )}
    </div>

  );
};

export default Dashboard;
