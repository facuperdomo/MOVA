// src/components/paymentqr/PaymentQR.js
import React, { useEffect, useState } from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import { API_URL } from '../../config/apiConfig';

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    console.log("PaymentQR: iniciando fetch para monto:", amount);

    // Recupera el ID de la empresa y el token desde localStorage
    const companyId = localStorage.getItem("companyId"); 
    const token = localStorage.getItem("token"); 

    // Si no tienes el companyId en localStorage, podrías recibirlo como prop
    // o buscarlo de otra forma, pero aquí se asume que está en localStorage.
    if (!companyId) {
      console.error("No se encontró companyId en localStorage.");
      setLoading(false);
      return;
    }

    // Construye la URL con el ID de la empresa
    const url = `${API_URL}/api/mercadopago/create-preference/${companyId}`;

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Incluye el token JWT si tu backend exige autenticación
        'Authorization': `Bearer ${token}`
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
