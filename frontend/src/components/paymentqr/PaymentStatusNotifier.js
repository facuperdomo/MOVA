import React, { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_URL } from '../../config/apiConfig';

const PaymentStatusNotifier = () => {
  const [paymentStatus, setPaymentStatus] = useState('');

  useEffect(() => {
    const socket = new SockJS(`${API_URL}/ws-sockjs`);
    const stompClient = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Conectado a WebSocket');
        stompClient.subscribe('/topic/payment-status', message => {
          console.log('Mensaje recibido:', message.body);
          setPaymentStatus(message.body);
        });
      },
      onStompError: (frame) => {
        console.error('Error en STOMP:', frame);
      },
    });

    stompClient.activate();

    return () => {
      stompClient.deactivate();
    };
  }, []);

  return (
    <div>
      {paymentStatus && (
        <div className="payment-status">
          Estado del pago: {paymentStatus}
        </div>
      )}
    </div>
  );
};

export default PaymentStatusNotifier;
