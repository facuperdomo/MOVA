import React, { useEffect, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import "./paymentQRStyle.css";

const PaymentQR = ({ amount, onPaymentSuccess }) => {
  const [qrUrl, setQrUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  // Trae branchId una sola vez
  const branchId = localStorage.getItem("branchId");

  useEffect(() => {
    if (!branchId) {
      setErrorMessage("No se encontró la identificación de la sucursal.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage("");

    customFetch(
      // Usa la opción que coincida con tu backend
      `${API_URL}/api/mercadopago/create-preference/${branchId}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ amount }),
      }
    )
      .then((data) => {
        if (data.error) {
          throw new Error(data.error);
        }
        setQrUrl(data.init_point);
      })
      .catch((err) => {
        console.error("💥 Error generando QR:", err);
        setErrorMessage(
          err.data?.error || err.message || "Error al generar el QR."
        );
      })
      .finally(() => {
        setLoading(false);
      });
  }, [amount, branchId]);

  return (
    <div className="payment-qr-container">
      {loading && <p className="loading-message">Generando código QR...</p>}

      {!loading && errorMessage && (
        <p className="error-message">{errorMessage}</p>
      )}

      {!loading && !errorMessage && qrUrl && (
        <div className="qr-content">
          <QRCodeCanvas className="qr-canvas" value={qrUrl} size={200} />
          <p className="amount-label">Total a pagar: ${amount}</p>
          {onPaymentSuccess && (
            <button
              className="confirm-payment-btn"
              onClick={onPaymentSuccess}
            >
              ✅ Ya pagué
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default PaymentQR;
