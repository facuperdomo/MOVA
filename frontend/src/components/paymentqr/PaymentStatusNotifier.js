import React, { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_URL, WS_URL } from '../../config/apiConfig';

const PaymentStatusNotifier = ({ userId }) => {
  const [paymentStatus, setPaymentStatus] = useState('');

  useEffect(() => {
    if (!userId) return;

    const socket = new SockJS(`${WS_URL}/ws-sockjs`);
    const stompClient = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Conectado a WebSocket para userId=', userId);
        // NOS SUSCRIBIMOS al canal individual
        stompClient.subscribe(
          `/topic/payment-status/user/${userId}`,
          message => {
            console.log('Mensaje recibido:', message.body);
            setPaymentStatus(message.body);
          }
        );
      },
      onStompError: frame => {
        console.error('Error en STOMP:', frame);
      },
    });

    stompClient.activate();
    return () => stompClient.deactivate();
  }, [userId]);

  // Oculta después de 5s
  useEffect(() => {
    if (!paymentStatus) return;
    const t = setTimeout(() => setPaymentStatus(''), 5000);
    return () => clearTimeout(t);
  }, [paymentStatus]);

  return paymentStatus ? (
    <div className="payment-status">
      Estado del pago: {paymentStatus}
    </div>
  ) : null;
};

export default PaymentStatusNotifier;
