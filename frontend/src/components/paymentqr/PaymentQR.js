import React, { useEffect, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { customFetch } from "../../utils/api";
import "./paymentQRStyle.css";

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const branchId = localStorage.getItem("branchId");
    if (!branchId) {
      setErrorMessage("No se encontró la identificación de la sucursal.");
      setLoading(false);
      return;
    }

    customFetch(`/api/mercadopago/create-preference/${branchId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ amount }),
    })
      .then((data) => {
        console.log("💾 Respuesta create-preference:", data);
        if (data.error) {
          throw new Error(data.error);
        }
        setQrUrl(data.init_point);
      })
      .catch((err) => {
        console.error("💥 Error generando QR:", err);
        // si el backend retorna { error: '...' } lo vemos en err.data o err.message
        setErrorMessage(
          err.data?.error || err.message || "Error al generar el QR."
        );
      })
      .finally(() => {
        setLoading(false);
      });
  }, [amount]);

  return (
    <div className="payment-qr-container">
      {loading ? (
        <p className="loading-message">Generando código QR...</p>
      ) : errorMessage ? (
        <p className="error-message">{errorMessage}</p>
      ) : qrUrl ? (
        <div className="qr-content">
          <QRCodeCanvas className="qr-canvas" value={qrUrl} size={256} />
        </div>
      ) : (
        <p className="error-message">Ocurrió un error al generar el código QR.</p>
      )}
    </div>
  );
};

export default PaymentQR;
