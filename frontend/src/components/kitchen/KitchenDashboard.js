// src/components/kitchen/KitchenDashboard.js
import React, { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { customFetch } from '../../utils/api';
import { API_URL, WS_URL } from '../../config/apiConfig';
import './kitchenDashboardStyle.css';
import { useNavigate } from 'react-router-dom';

const KitchenDashboard = () => {
  const [orders, setOrders] = useState([]);
  const [prodsMap, setProdsMap] = useState({});
  const navigate = useNavigate();

  const STATUS_RANK = {
    SENT_TO_KITCHEN: 1,
    PREPARING: 2,
    READY: 3,
    COMPLETED: 4,
  };

  function maxStatus(a, b) {
    const ra = STATUS_RANK[a] ?? 0;
    const rb = STATUS_RANK[b] ?? 0;
    return ra >= rb ? a : b;
  }

  function coalesceOrders(list, groupKitchenItems) {
    const byKey = new Map();
    for (const o of list) {
      const ex = byKey.get(o.orderKey);
      if (!ex) {
        byKey.set(o.orderKey, o);
      } else {
        byKey.set(o.orderKey, {
          ...ex,
          // fusiono items ya agrupados
          items: groupKitchenItems([...(ex.items || []), ...(o.items || [])]),
          // conservo el estado “más avanzado”
          kitchenStatus: maxStatus(ex.kitchenStatus, o.kitchenStatus),
          // opcional: la fecha del último evento
          dateTime: o.dateTime || ex.dateTime,
        });
      }
    }
    return Array.from(byKey.values());
  }

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

  useEffect(() => {
    let stompClient;
    (async () => {
      // 1) Precarga productos
      const prods = await customFetch(`${API_URL}/api/products`);
      const map = Object.fromEntries(prods.map(p => [p.id, p]));
      setProdsMap(map);

      // 2) Trae las órdenes ya pendientes por REST
      try {
        console.log("↪ KitchenDashboard: llamando GET /api/kitchen/orders");
        const pending = await customFetch(`${API_URL}/api/kitchen/orders`);
        console.log("↪ KitchenDashboard: raw pending orders:", pending);
        // ⚠️ Filtrar órdenes sin ítems (por si acaso)
        const nonEmpty = pending.filter(o => Array.isArray(o.items) && o.items.length > 0);
        console.log(`↪ KitchenDashboard: ${pending.length - nonEmpty.length} órdenes filtradas por estar vacías`);
        const enrichedPending = nonEmpty.map(order => enrichOrder(order, map));
        const coalesced = coalesceOrders(enrichedPending, groupKitchenItems);
        setOrders(coalesced);
      } catch (e) {
        console.error("No pude cargar órdenes pendientes:", e);
      }

      // 3) Conecta al WS
      const token = localStorage.getItem('token');
      const socketUrl = `${WS_URL.replace(/\/$/, "")}/ws-sockjs?token=${token}`;
      const socket = new SockJS(socketUrl);
      stompClient = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        debug: msg => console.log("STOMP>", msg),
      });

      stompClient.onConnect = () => {
        stompClient.subscribe('/topic/kitchen-orders', ({ body }) => {
          const raw = JSON.parse(body);
          if (!Array.isArray(raw.items) || raw.items.length === 0) return;

          const enriched = enrichOrder(raw, map);
          setOrders(prev => {
            const idx = prev.findIndex(o => o.orderKey === enriched.orderKey);
            if (idx === -1) return [...prev, enriched];

            const current = prev[idx];
            const override = shouldOverrideStatus(current, enriched);
            const next = [...prev];
            next[idx] = {
              ...current,
              ...enriched,
              items: enriched.items, // snapshot: reemplazo
              kitchenStatus: override
                ? enriched.kitchenStatus
                : maxStatus(current.kitchenStatus, enriched.kitchenStatus),
              dateTime: enriched.dateTime || current.dateTime,
            };
            return next;
          });
        });
      };

      stompClient.activate();
    })();

    return () => stompClient && stompClient.deactivate();
  }, []);

  function groupKitchenItems(items) {
    const map = new Map();
    for (const it of items || []) {
      const key = `${it.productId}|${(it.ingredientIds || [])
        .slice()
        .sort((a, b) => a - b)
        .join(',')}`;
      if (map.has(key)) {
        map.get(key).quantity += (it.quantity ?? 1);
      } else {
        map.set(key, { ...it });
      }
    }
    return Array.from(map.values());
  }

  function enrichOrder(order, prodsMap) {
    // 1) map: enriquecer cada ítem con nombres y "removedIngredients"
    const mappedItems = (order.items || []).map(it => {
      const prod = prodsMap[it.productId] || { name: `#${it.productId}`, ingredients: [] };
      const ingredientIds = Array.isArray(it.ingredientIds) ? it.ingredientIds : [];
      const removed = (prod.ingredients || [])
        .filter(ing => !ingredientIds.includes(ing.id))
        .map(ing => ing.name);

      return {
        ...it,
        productName: prod.name,
        removedIngredients: removed
      };
    });

    // 2) group: agrupar por productId + ingredientIds y sumar quantity
    const groupedItems = groupKitchenItems(mappedItems);

    // 3) armar la orden enriquecida
    const isSale = order.id != null;
    const id = isSale ? order.id : order.accountId;
    const orderType = isSale ? 'SALE' : 'ACCOUNT';

    return {
      id,
      orderKey: `${orderType}-${id}`,
      orderType,
      aggregated: !!order.aggregated,
      accountName: order.accountName || '',
      dateTime: order.dateTime,
      kitchenStatus: order.kitchenStatus || 'SENT_TO_KITCHEN',
      items: groupedItems
    };
  }

  function shouldOverrideStatus(current, incoming) {
    // Si es snapshot agregado de CUENTA, confiamos en él
    if (incoming.orderType === 'ACCOUNT' && incoming.aggregated) return true;
    // Si la UI quedó en COMPLETED y llega algo no completado, reabrimos
    if (current.kitchenStatus === 'COMPLETED' && incoming.kitchenStatus !== 'COMPLETED') return true;
    return false;
  }

  const updateKitchenStatus = async (order, newStatus) => {
    try {
      const url = order.orderType === 'SALE'
        ? `${API_URL}/api/kitchen/orders/${order.id}/kitchen-status`
        : `${API_URL}/api/accounts/${order.id}/kitchen-status`;

      const updated = await customFetch(url, {
        method: 'PUT',
        body: JSON.stringify({ kitchenStatus: newStatus })
      });

      // Para ACCOUNT: 'updated' ya es snapshot agregado; para SALE: es la venta filtrada
      const enriched = enrichOrder(updated, prodsMap);

      setOrders(prev => {
        const idx = prev.findIndex(o => o.orderKey === enriched.orderKey);
        // si no estaba, lo agregamos
        if (idx === -1) return [...prev, enriched];

        const current = prev[idx];

        // Reglas para decidir si sobrescribimos el estado:
        // 1) Snapshot agregado de cuenta => confiamos en él
        // 2) Si la tarjeta quedó en COMPLETED y llega algo no completado => reabrimos
        const override =
          (enriched.orderType === 'ACCOUNT' && !!enriched.aggregated) ||
          (current.kitchenStatus === 'COMPLETED' && enriched.kitchenStatus !== 'COMPLETED');

        const next = [...prev];
        next[idx] = {
          ...current,
          ...enriched,
          // snapshot: reemplazamos completamente los ítems
          items: enriched.items,
          // estado: o lo sobrescribimos, o usamos el “más avanzado”
          kitchenStatus: override
            ? enriched.kitchenStatus
            : maxStatus(current.kitchenStatus, enriched.kitchenStatus),
          dateTime: enriched.dateTime || current.dateTime,
        };
        return next;
      });
    } catch (err) {
      console.error('Error actualizando estado de cocina', err);
    }
  };

  return (
    <div className="kitchen-dashboard">
      <div className="kitchen-header">
        <h2>Órdenes en Cocina</h2>
        <button className="logout-btn" onClick={handleLogout}>🚪</button>
      </div>
      <div className="orders-grid">
        {orders.filter(o => o.kitchenStatus !== 'COMPLETED').map(order => (
          <div key={order.orderKey} className="order-card">
            <h3>Orden #{order.id}</h3>
            <ul>
              {order.items.map((it, i) => {
                const ingKey = Array.isArray(it.ingredientIds) ? it.ingredientIds.join('-') : 'noings';
                return (
                  <li key={`${order.orderKey}-${it.productId}-${ingKey}-${i}`}>
                    {it.productName} x{it.quantity}
                    {it.removedIngredients.length > 0 && (
                      <span className="removed"> – sin {it.removedIngredients.join(', ')}</span>
                    )}
                  </li>
                );
              })}
            </ul>
            <div className="action-buttons">
              {order.kitchenStatus === 'SENT_TO_KITCHEN' && (
                <button className="action-btn" onClick={() => updateKitchenStatus(order, 'PREPARING')}>
                  Marcar preparando
                </button>
              )}
              {order.kitchenStatus === 'PREPARING' && (
                <button className="action-btn" onClick={() => updateKitchenStatus(order, 'READY')}>
                  Marcar listo
                </button>
              )}
              {order.kitchenStatus === 'READY' && (
                <button className="action-btn" onClick={() => updateKitchenStatus(order, 'COMPLETED')}>
                  Marcar completado
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default KitchenDashboard;
