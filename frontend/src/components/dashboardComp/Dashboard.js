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
  printItemsReceipt,
  printFrontOrder
} from "../../utils/print";
import PaymentStatusNotifier from '../paymentqr/PaymentStatusNotifier';
import AddMesaModal from "../addMesaModal/AddMesaModal";
import PaymentOptionsModal from "../account/PaymentOptionsModal";
import AccountsListModal from "../account/AccountsListModal";
import AccountPaymentsModal from "../account/AccountPaymentsModal";
import SelectDeviceModal from "../common/SelectDeviceModal";

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
  const [enablePrinting, setEnablePrinting] = useState(true);

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

  const [showClosePrintModal, setShowClosePrintModal] = useState(false);
  const [pendingCloseAccountId, setPendingCloseAccountId] = useState(null);

  const [showErrorModal, setShowErrorModal] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const [devices, setDevices] = useState([]);
  const [showDeviceModal, setShowDeviceModal] = useState(false);

  const [cashBoxCode, setCashBoxCode] = useState(null);

  const [showAllPaidModal, setShowAllPaidModal] = useState(false);

  const [userId, setUserId] = useState(null);

  const cartRef = useRef(cart);
  const totalRef = useRef(total);

  useEffect(() => {
    const branchId = localStorage.getItem("branchId");
    const token = localStorage.getItem("token");
    const hasDevice = Boolean(localStorage.getItem("deviceId"));

    if (branchId && !hasDevice && enablePrinting) {
      customFetch(`${API_URL}/api/branches/${branchId}/devices`, {
        headers: { "Authorization": `Bearer ${token}` }
      })
        .then((list) => {
          console.log("Dispositivos disponibles:", list);
          setDevices(list);
          setShowDeviceModal(true);
        })
        .catch(err => {
          console.error("Error al cargar dispositivos:", err);
          setErrorMessage("No fue posible obtener la lista de terminales.");
          setShowErrorModal(true);
        });
    }
  }, []);

  useEffect(() => {
    cartRef.current = cart;
    totalRef.current = total;
  }, [cart, total]);

  useEffect(() => {
    const storedBranchId = localStorage.getItem("branchId");
    const storedUserId = localStorage.getItem("userId");

    if (storedBranchId) setBranchId(parseInt(storedBranchId));
    if (storedUserId) setUserId(parseInt(storedUserId));
    (async () => {
      try {
        const branch = await customFetch(`${API_URL}/api/branch/me`);
        console.log("flag enableIngredients:", branch.enableIngredients);
        console.log("flag enablePrinting:", branch.enablePrinting);
        setEnableIngredients(branch.enableIngredients);
        setEnablePrinting(branch.enablePrinting);
      } catch (err) {
        console.error("Error al cargar configuración de sucursal:", err);
      }
    })();
    loadAccounts(storedBranchId);
  }, []);

  // Suscripción a WebSocket para recibir notificaciones de pago
  useEffect(() => {
    if (offline || !userId) {
      console.log("Offline: no inicializo STOMP/SockJS");
      return;
    }
    console.log("Iniciando STOMP/SockJS a:", WS_URL, "para userId=", userId);

    const client = new Client({
      // 1) Creamos la conexión SockJS
      webSocketFactory: () => new SockJS(`${WS_URL}/ws-sockjs`),
      reconnectDelay: 5000,
      debug: (str) => console.log("STOMP/SockJS DEBUG:", str),
    });

    client.onConnect = () => {
      // 2) Nos suscribimos al topic de estado de pago
      client.subscribe(`/topic/payment-status/user/${userId}`, async (msg) => {
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
            setErrorMessage("Ocurrió un error guardando la venta QR.");
            setShowErrorModal(true);
          }
        }
      }, { id: "payment-status-sub" });
    };

    client.onStompError = frame => console.error("Error STOMP:", frame.body);
    client.activate();

    return () => client.deactivate();
  }, [offline, userId]);

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

  const handleConfirmClosePrintWithValidation = async () => {
    if (!enablePrinting) {
      await confirmCloseWithoutPrint();
      return;
    }
    const deviceId = localStorage.getItem("deviceId");
    if (!validateDevice()) return;
    // si existe, delega al confirmCloseAndPrint original
    await confirmCloseAndPrint();
  };

  const loadAccountItems = async (accountId) => {
    try {
      // 1️⃣ Mira qué trae el fetch en bruto
      const account = await customFetch(`${API_URL}/api/accounts/${accountId}`);
      console.log('🔍 raw account response:', account);

      // 2️⃣ Inspecciona los items antes de normalizar
      console.log('🔍 raw account.items:', account.items);

      const normalized = account.items.map(line => {
        // 2.1️⃣ Qué ingredientIds vinieron
        console.log(`  • item ${line.id} ingredientIds:`, line.ingredientIds);

        const prod = products.find(p => p.id === line.productId) || { name: '', ingredients: [], price: 0 };
        console.log(`    · producto lookup:`, prod);

        const hasIds = Array.isArray(line.ingredientIds);
        const keptIds = hasIds
          ? line.ingredientIds          // si viene [], keptIds=[] → quitar todos
          : prod.ingredients.map(i => i.id); // sólo cuando es undefined uso todos
        console.log(`    · keptIds after fallback:`, keptIds);

        const removedNames = hasIds
          ? prod.ingredients.filter(i => !keptIds.includes(i.id)).map(i => i.name)
          : [];
        const displayName = removedNames.length
          ? `${prod.name} – sin ${removedNames.join(', ')}`
          : prod.name;

        const item = {
          id: line.id,
          productId: line.productId,
          productName: prod.name,
          displayName,
          price: prod.price || 0,
          quantity: line.quantity,
          ingredients: keptIds.map(id => {
            const ing = prod.ingredients.find(x => x.id === id);
            return { id, name: ing?.name ?? '' };
          }),
          paid: line.paid
        };
        console.log(`    · normalized item:`, item);
        return item;
      });

      // 3️⃣ Antes de agrupar, comprueba tu array normalizado
      console.log('🔍 normalized array:', normalized);

      const grouped = groupItems(normalized);
      console.log('🔍 grouped array:', grouped);

      setAccountItems(grouped);
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
    let status = await customFetch(
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

  const validateDevice = () => {
    const deviceId = localStorage.getItem("deviceId");
    if (!deviceId) {
      setErrorMessage(
        "No se seleccionó el dispositivo que se está utilizando; por favor, elige uno nuevamente."
      );
      setShowErrorModal(true);
      return false;
    }
    return true;
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
      // Asumo que este endpoint devuelve { open: boolean, code: string, … }
      const status = await customFetch(`${API_URL}/api/cash-box/status-for-user`);
      setIsCashRegisterOpen(status.open);
      setCashBoxCode(status.code);
    } catch (error) {
      console.error("Error al verificar la caja:", error);
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

      await loadAccountItems(selectedAccountId);
    } catch (err) {
      console.error("Error decrementando ítem de cuenta:", err);
      setErrorMessage(err.data?.message || err.message);
      setShowErrorModal(true);
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

    try {
      // ← Aquí definimos thisIds correctamente
      const thisIds = product.ingredients?.map(i => i.id) || [];

      // Buscamos si ya existe esa variante en la cuenta
      const existing = accountItems.find(it =>
        it.productId === product.id &&
        sameIngredientSet(it.ingredients.map(i => i.id), thisIds)
      );

      if (existing) {
        // 2a) Si existe, incrementa cantidad
        await customFetch(
          `${API_URL}/api/accounts/${selectedAccountId}/items/${existing.id}`,
          {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ quantity: existing.quantity + 1 })
          }
        );
      } else {
        // 2b) Si no existe, crea nuevo con ingredientIds=thisIds
        await customFetch(
          `${API_URL}/api/accounts/${selectedAccountId}/items`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              productId: product.id,
              quantity: 1,
              ingredientIds: thisIds
            })
          }
        );
      }

      // 3) Refresca siempre la lista
      await loadAccountItems(selectedAccountId);
    } catch (err) {
      console.error("Error agregando a cuenta:", err);
      setErrorMessage("No se pudo actualizar la cuenta");
      setShowErrorModal(true);
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
      localStorage.removeItem('deviceId');
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

      // 2) Si la impresión está desactivada, simplemente salir
      if (!enablePrinting) return;

      // 3) Imprimir si corresponde
      if (!validateDevice()) return;
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
      setErrorMessage("Ocurrió un error al procesar la venta. Inténtelo de nuevo.");
      setShowErrorModal(true);
    }
  };

  const undoLastSale = () => {
    if (!lastSale) {
      setErrorMessage("No hay ventas para deshacer.");
      setShowErrorModal(true);
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
      setErrorMessage("No se pudo deshacer la última venta.");
      setShowErrorModal(true);
    }
  };

  const handleRetryPrint = async () => {
    console.log("🚀 handleRetryPrint arrancó");       // ①
    if (!enablePrinting) {
      setErrorMessage("La impresión está desactivada para esta sucursal.");
      setShowErrorModal(true);
      return;
    }
    if (!validateDevice()) return;
    if (!lastSale) return;

    setIsPrinting(true);
    setPrintError(false);

    try {
      console.log("🔄 Reintentando printOrder...", lastSale);  // ②
      await printOrder(lastSale);
      console.log("✅ printOrder salió bien");                 // ③
    } catch (err) {
      // ④ esto SÍ debería aparecer en la consola
      console.error("❌ Reintento de impresión fallido:", err);
      setErrorMessage(
        err.message ||
        "Hubo un error imprimiendo. Verifica la impresora asignada."
      );
      setShowErrorModal(true);
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
            setErrorMessage("La sincronización automática falló porque tu sesión expiró. Por favor, inicia sesión nuevamente.");
            setShowErrorModal(true);
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
      const key = `${prodId}-${ingredientsKey}-${item.displayName || ''}`;

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

  const displayItems = groupItems(
    selectedAccountId
      ? accountItems
      : cart
  );

  const handleCloseAccount = async (accountId) => {
    try {
      // 1) Obtenemos estado de split para saber si falta algo por pagar
      const status = await customFetch(
        `${API_URL}/api/accounts/${accountId}/split/status`
      );
      const remainingMoney = status.currentTotal - (status.paidMoney || 0);

      if (remainingMoney <= 0) {
        setPendingCloseAccountId(accountId);
        setShowClosePrintModal(true);
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
      setErrorMessage("No se pudo procesar el cierre de la cuenta.");
      setShowErrorModal(true);
    }
  };

  // Confirmar cierre e impresión
  const confirmCloseAndPrint = async () => {
    const accountId = pendingCloseAccountId;
    // si no hay impresión, lo cierras sin imprimir
    if (!enablePrinting) {
      await confirmCloseWithoutPrint();
      return;
    }
    if (!validateDevice()) return;

    try {
      // 1) cierro la cuenta y recibo el recibo
      const receipt = await customFetch(
        `${API_URL}/api/accounts/${accountId}/close`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            code: cashBoxCode,
            amount: currentTotal,
            paymentMethod: "CUENTA",
            payerName: "–",
          }),
        }
      );

      // 2) intento imprimir
      setIsPrinting(true);
      setPrintError(false);
      try {
        await printOrder(receipt);
        // 🟢 sólo si imprime OK, limpio la UI:
        setShowClosePrintModal(false);
        setSelectedAccountId(null);
        setPendingCloseAccountId(null);
        await loadAccounts(branchId);
      } catch (err) {
        console.error("❌ Error imprimiendo al cerrar cuenta:", err);
        setErrorMessage(
          err.message ||
          "Hubo un error imprimiendo el recibo. La cuenta sigue abierta."
        );
        setShowErrorModal(true);
        setPrintError(true);
        // <-- NO limpiamos pendingCloseAccountId, sigue disponible “Reintentar impresión”
      } finally {
        setIsPrinting(false);
      }
    } catch (err) {
      console.error("Error cerrando la cuenta:", err);
      setErrorMessage("No se pudo cerrar la cuenta en el servidor.");
      setShowErrorModal(true);
    }
  };

  // Confirmar cierre sin imprimir
  const confirmCloseWithoutPrint = async () => {
    const accountId = pendingCloseAccountId;
    if (!accountId) return;

    try {
      await customFetch(
        `${API_URL}/api/accounts/${accountId}/close`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            code: cashBoxCode,
            amount: currentTotal,
            paymentMethod: "CUENTA",
            payerName: "–",
          }),
        }
      );
    } catch (err) {
      // Si ya estaba cerrada, no mostramos error
      if (!err.message.includes("ya está cerrada")) {
        console.error("Error cerrando sin imprimir:", err);
        setErrorMessage("No se pudo cerrar la cuenta.");
        setShowErrorModal(true);
        return;
      }
    }

    // En cualquier caso (éxito o ya cerrada), limpiamos:
    setShowClosePrintModal(false);
    setSelectedAccountId(null);
    setPendingCloseAccountId(null);
    await loadAccounts(branchId);
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

      const remaining = stat.currentTotal - (stat.paidMoney || 0);

      // -- Nuevo bloque: si no hay nada por pagar, muestras el modal y sales --
      if (remaining <= 0) {
        setShowAllPaidModal(true);
        return;
      }
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
      setErrorMessage("No se pudo abrir el modal de pago.");
      setShowErrorModal(true);
    }
  };

  const handlePrint = async ({ type, payload }) => {
    if (!enablePrinting) {
      // si la impresión está deshabilitada, no hacemos nada
      return;
    }
    if (!validateDevice()) return;

    setIsPrinting(true);
    setPrintError(false);
    try {
      console.log(`🖨️ handlePrint → type=${type}`, payload);
      if (type === "PRODUCT_PAYMENT") {
        await printItemsReceipt(payload);
      } else if (type === "FULL_CLOSURE" || type === "PARTIAL_PAYMENT") {
        // para imprimir el cierre con todos los datos que mandó el front
        await printFrontOrder(payload);
      } else {
        // tu flujo legacy
        await printOrder(payload);
      }
    } catch (err) {
      console.error("Error imprimiendo ticket:", err);
      // Muestra el mensaje en tu modal en vez de alert
      setErrorMessage(
        err?.message ||
        "Hubo un error imprimiendo. Verifica la impresora asignada."
      );
      setShowErrorModal(true);
      setPrintError(true);
    } finally {
      setIsPrinting(false);
    }
  };

  return (
    <div className="app-container">
      {enablePrinting && showDeviceModal && (
        <SelectDeviceModal
          devices={devices}
          onSelect={(id) => {
            if (id) localStorage.setItem("deviceId", id);
            setShowDeviceModal(false);
          }}
        />
      )}
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
                <span>🧾 Cuenta seleccionada: {acc?.name}</span>
                <button className="remove-account-btn" onClick={() => { setSelectedAccountId(null); setAccountItems([]); }} style={{ marginLeft: "1rem" }}>
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
              const name = item.displayName || (selectedAccountId ? item.productName : item.name);

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
                    disabled={item.paid /* no borres si ya está pagado */}
                    title={item.paid ? "Este ítem ya fue pagado y no puede eliminarse" : ""}
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
            {lastSale && !selectedAccountId && (
              <div className="sale-actions">
                <button className="undo-sale-btn" onClick={undoLastSale}>
                  Deshacer venta
                </button>

                {!enablePrinting ? null : isPrinting ? (
                  <button className="popup-btn-print" disabled>
                    Imprimiendo…
                  </button>
                ) : printError ? (
                  <button className="popup-btn-print" onClick={handleRetryPrint}>
                    Reintentar impresión
                  </button>
                ) : (
                  <PrintButton
                    order={lastSale}
                    onError={(err) => {
                      setErrorMessage(
                        err.message ||
                        "Hubo un error imprimiendo. Verifica la impresora asignada."
                      );
                      setShowErrorModal(true);
                      setPrintError(true);
                    }}
                    onSuccess={() => {
                      // opcional: si quieres limpiar el error tras éxito
                      setPrintError(false);
                    }}
                  />
                )}
              </div>
            )}

          </div>
        </div>
      </div>

      {showClosePrintModal && (
        <div
          className="confirm-close-overlay"
          onClick={e => {
            // Si clicas fuera del modal, ciérralo
            if (e.target.classList.contains("confirm-close-overlay")) {
              setShowClosePrintModal(false);
            }
          }}
        >
          <div className="confirm-close-modal" style={{ position: 'relative' }}>
            {/* Botón "X" para cerrar */}
            <X
              size={24}
              className="confirm-close-modal__close"
              onClick={() => setShowClosePrintModal(false)}
              style={{ position: "absolute", top: 8, right: 8, cursor: "pointer" }}
            />

            <h2>
              {enablePrinting
                ? "¿Deseas imprimir lo vendido?"
                : "¿Deseas cerrar la cuenta?"}
            </h2>

            <div className="popup-buttons">
              <button
                className="popup-btn popup-btn-cash"
                onClick={handleConfirmClosePrintWithValidation}
              >
                {enablePrinting ? "Sí, imprimir" : "Sí, cerrar"}
              </button>

              {enablePrinting && (
                <button
                  className="popup-btn popup-btn-qr"
                  onClick={() => {
                    setShowClosePrintModal(false);
                    confirmCloseWithoutPrint();
                  }}
                >
                  No, sólo cerrar
                </button>
              )}
            </div>
          </div>
        </div>
      )}

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
            <X
              className="popup-close"
              size={32}
              onClick={() => setCustomizingProduct(null)}
            />
            <h3>{customizingProduct.name} – Quita Ingredientes</h3>
            <div className="ingredient-list">
              {customizingProduct.ingredients.map(ing => (
                <label key={ing.id} className="ingredient-item">
                  <input
                    type="checkbox"
                    checked={tempIngredients.includes(ing.id)}
                    onChange={() =>
                      setTempIngredients(t =>
                        t.includes(ing.id)
                          ? t.filter(x => x !== ing.id)
                          : [...t, ing.id]
                      )
                    }
                  />
                  {ing.name}
                </label>
              ))}
            </div>
            <button
              className="popup-btn popup-btn-cash"
              onClick={async () => {
                // Ingredientes escogidos y removidos
                const kept = customizingProduct.ingredients.filter(i =>
                  tempIngredients.includes(i.id)
                );
                const removedNames = customizingProduct.ingredients
                  .filter(i => !tempIngredients.includes(i.id))
                  .map(i => i.name);
                // Nombre para mostrar
                const displayName = removedNames.length
                  ? `${customizingProduct.name} – sin ${removedNames.join(", ")}`
                  : customizingProduct.name;

                if (selectedAccountId) {
                  try {
                    // 1) insertamos en el back
                    const created = await customFetch(
                      `${API_URL}/api/accounts/${selectedAccountId}/items`,
                      {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                          productId: customizingProduct.id,
                          quantity: 1,
                          ingredientIds: kept.map(i => i.id),
                        }),
                      }
                    );
                    // 2) construimos en local nuestro nuevo ítem, con displayName
                    const newItem = {
                      id: created.id,                     // id que devolvió el back
                      productId: customizingProduct.id,
                      productName: customizingProduct.name,
                      displayName,                        // el nombre “– sin X”
                      price: customizingProduct.price,
                      quantity: 1,
                      ingredients: kept.map(i => ({ id: i.id, name: i.name })),
                      paid: false
                    };
                    // 3) lo añadimos a la lista local (sin recargar toda)
                    setAccountItems(prev => [...prev, newItem]);
                  } catch (err) {
                    console.error("Error agregando a cuenta:", err);
                    setErrorMessage("No se pudo agregar el producto a la cuenta.");
                    setShowErrorModal(true);
                  }
                }
                else {
                  // Agregar al carrito normal
                  addToCart({
                    ...customizingProduct,
                    ingredients: kept,
                    displayName,
                  });
                }

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
        <div
          className="popup-overlay table-detail-modal"
          onClick={(e) => {
            if (e.target.classList.contains("popup-overlay")) {
              closeDetailModal();
            }
          }}
        >
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={closeDetailModal} />
            <h2 className="table-detail-title">
              {detailAccount.name} (#{detailAccount.id})
            </h2>

            <table className="detail-table">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th>Cantidad</th>
                </tr>
              </thead>
              <tbody>
                {detailAccount.items.map((i) => (
                  <tr key={i.id}>
                    <td>{i.productName}</td>
                    <td>{i.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showAllPaidModal && (
        <div className="popup-overlay" onClick={() => setShowAllPaidModal(false)}>
          <div className="popup-content">
            <X
              className="popup-close"
              size={32}
              onClick={() => setShowAllPaidModal(false)}
            />
            <h2>Todo pagado</h2>
            <p>La cuenta ya no tiene saldo pendiente.</p>
            <button
              className="popup-btn"
              onClick={() => setShowAllPaidModal(false)}
            >
              Aceptar
            </button>
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
          cashBoxCode={cashBoxCode}
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

      {showErrorModal && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X
              className="popup-close"
              size={32}
              onClick={() => setShowErrorModal(false)}
            />
            <h2>Error</h2>
            <p>{errorMessage}</p>

            {/* Si es error de dispositivo, damos opción extra */}
            {errorMessage.includes("dispositivo") && (
              <button
                className="popup-btn popup-btn-primary"
                onClick={() => {
                  setShowErrorModal(false);
                  setShowDeviceModal(true);
                }}
              >
                Seleccionar dispositivo
              </button>
            )}

            <button
              className="popup-btn popup-btn-secondary"
              onClick={() => setShowErrorModal(false)}
            >
              Aceptar
            </button>
          </div>
        </div>
      )}
    </div>

  );
};

export default Dashboard;
