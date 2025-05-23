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
        const pending = await customFetch(`${API_URL}/api/kitchen/orders`);
        const enrichedPending = pending.map(order => enrichOrder(order, map));
        setOrders(enrichedPending);
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
          const enriched = enrichOrder(raw, map);
          setOrders(prev =>
            prev.some(o => o.id === enriched.id) ? prev : [...prev, enriched]
          );
        });
      };

      stompClient.activate();
    })();

    return () => stompClient && stompClient.deactivate();
  }, []);

  function enrichOrder(order, prodsMap) {
    const items = order.items.map(it => {
      const prod = prodsMap[it.productId] || { name: `#${it.productId}`, ingredients: [] };
      const removed = prod.ingredients
        .filter(ing => !(it.ingredientIds || []).includes(ing.id))
        .map(ing => ing.name);

      return { ...it, productName: prod.name, removedIngredients: removed };
    });
    return { ...order, items };
  }

  const updateKitchenStatus = async (orderId, newStatus) => {
    try {
      const updated = await customFetch(
        `${API_URL}/api/kitchen/orders/${orderId}/kitchen-status`,
        {
          method: 'PUT',
          body: JSON.stringify({ kitchenStatus: newStatus })
        }
      );
      const enriched = enrichOrder(updated, prodsMap);
      setOrders(prev => prev.map(o => (o.id === enriched.id ? enriched : o)));
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
          <div key={order.id} className="order-card">
            <h3>Orden #{order.id}</h3>
            <ul>
              {order.items.map((it, i) => (
                <li key={i}>
                  {it.productName} x{it.quantity}
                  {it.removedIngredients.length > 0 && (
                    <span className="removed">
                      – sin {it.removedIngredients.join(', ')}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            <div className="action-buttons">
              {order.kitchenStatus === 'SENT_TO_KITCHEN' && (
                <button className="action-btn"
                  onClick={() => updateKitchenStatus(order.id, 'PREPARING')}>
                  Marcar preparando
                </button>
              )}
              {order.kitchenStatus === 'PREPARING' && (
                <button className="action-btn"
                  onClick={() => updateKitchenStatus(order.id, 'READY')}>
                  Marcar listo
                </button>
              )}
              {order.kitchenStatus === 'READY' && (
                <button className="action-btn"
                  onClick={() => updateKitchenStatus(order.id, 'COMPLETED')}>
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
