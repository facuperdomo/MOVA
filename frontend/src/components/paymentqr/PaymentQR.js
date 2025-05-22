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
      body: JSON.stringify({ amount })
    })
    .then(data => {
      console.log("🚀 create-preference response:", data);
      // fallback sandbox si hace falta
      const url = data.init_point || data.sandbox_init_point;
      if (!url) {
        throw new Error(`No init_point ni sandbox_init_point:\n${JSON.stringify(data)}`);
      }
      setQrUrl(url);
    })
    .catch(err => {
      console.error("💥 Error generando QR:", err);
      setErrorMessage(err.message || "Error al generar el QR.");
    })
    .finally(() => setLoading(false));
  }, [amount]);

  return (
    <div className="payment-qr-container">
      {loading
        ? <p className="loading-message">Generando código QR…</p>
        : errorMessage
          ? <p className="error-message">{errorMessage}</p>
          : (
            qrUrl
              ? <QRCodeCanvas className="qr-canvas" value={qrUrl} size={256} />
              : <p className="error-message">Ocurrió un error al generar el código QR.</p>
          )
      }
    </div>
  );
};

export default PaymentQR;
