// src/components/kitchen/KitchenDashboard.js
import React, { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { customFetch } from '../../utils/api';
import { API_URL, WS_URL } from '../../config/apiConfig';
import './kitchenDashboardStyle.css';

const KitchenDashboard = () => {
  const [orders, setOrders] = useState([]);
  const [prodsMap, setProdsMap] = useState({});

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
      const socketUrl = `${WS_URL.replace(/\/$/, "")}/ws`;
      console.log("Conectando WS a:", socketUrl);
      const socket = new SockJS(socketUrl);
      stompClient = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        debug: msg => console.log("STOMP>", msg),
      });
  
      stompClient.onConnect = () => {
        console.log("STOMP conectado, suscribiéndome a /topic/kitchen-orders");
        stompClient.subscribe('/topic/kitchen-orders', ({ body }) => {
          const raw = JSON.parse(body);
          console.log("Llega por WS orden:", raw);
          const enriched = enrichOrder(raw, map);
          setOrders(prev => {
            if (prev.some(o => o.id === enriched.id)) {
              return prev;
            }
            return [...prev, enriched];
          });
        });
      };
  
      stompClient.onStompError = frame => {
        console.error("STOMP Error:", frame);
      };
  
      stompClient.activate();
    })();
  
    return () => stompClient && stompClient.deactivate();
  }, []);

  function enrichOrder(order, prodsMap) {
    const items = order.items.map(it => {
      const prod = prodsMap[it.productId] || { name: `#${it.productId}`, ingredients: [] };
      const removed = prod.ingredients
        .filter(ing => !it.ingredientIds.includes(ing.id))
        .map(ing => ing.name);
      return {
        ...it,
        productName: prod.name,
        removedIngredients: removed
      };
    });
    return { ...order, items };
  }

  // función para avanzar el estado en cocina
  const updateKitchenStatus = async (orderId, newStatus) => {
    try {
      const updated = await customFetch(
        `${API_URL}/api/kitchen/orders/${orderId}/kitchen-status`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ kitchenStatus: newStatus })
        }
      );

      // Enriquecemos el objeto que viene del PUT
      const enriched = enrichOrder(updated, prodsMap);

      setOrders(prev =>
        prev.map(o => (o.id === enriched.id ? enriched : o))
      );
    } catch (err) {
      console.error('Error actualizando estado de cocina', err);
    }
  };

  return (
    <div className="kitchen-dashboard">
      <h2>Órdenes en Cocina</h2>
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
                <button
                  className="action-btn"
                  onClick={() => updateKitchenStatus(order.id, 'PREPARING')}
                >
                  Marcar preparando
                </button>
              )}
              {order.kitchenStatus === 'PREPARING' && (
                <button
                  className="action-btn"
                  onClick={() => updateKitchenStatus(order.id, 'READY')}
                >
                  Marcar listo
                </button>
              )}
              {order.kitchenStatus === 'READY' && (
                <button
                  className="action-btn"
                  onClick={() => updateKitchenStatus(order.id, 'COMPLETED')}
                >
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
