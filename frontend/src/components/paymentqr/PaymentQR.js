// src/components/paymentqr/PaymentQR.js
import React, { useEffect, useState } from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import { API_URL } from '../../config/apiConfig';

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    console.log("PaymentQR: iniciando fetch para monto:", amount);
    fetch(`${API_URL}/api/mercadopago/create-preference`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ amount })
    })
      .then(response => {
        if (!response.ok) {
          throw new Error('Error en la respuesta del servidor');
        }
        return response.json();
      })
      .then(data => {
        console.log("PaymentQR: datos recibidos:", data);
        setQrUrl(data.init_point);
        setLoading(false);
      })
      .catch(error => {
        console.error("PaymentQR: Error creando la preferencia:", error);
        setLoading(false);
      });
  }, [amount]);

  return (
    <div>
      {loading ? (
        <p>Generando código QR...</p>
      ) : (
        <>
          <p>Escanea el código QR para pagar:</p>
          {qrUrl ? (
            <QRCodeCanvas value={qrUrl} size={256} />
          ) : (
            <p>Ocurrió un error al generar el código QR.</p>
          )}
        </>
      )}
    </div>
  );
};

export default PaymentQR;
