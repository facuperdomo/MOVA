import React, { useEffect, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { API_URL } from "../../config/apiConfig";
import { customFetch } from "../../utils/api";
import "./paymentQRStyle.css"; // <- tu CSS de la versión A

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const branchId = localStorage.getItem("branchId");
    if (!branchId) {
      setErrorMessage("No se encontró la identificación de la sucursl.");
      setLoading(false);
      return;
    }

    customFetch(
      `${API_URL}/api/mercadopago/create-preference/${branchId}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ amount }),
      }
    )
      .then((data) => {
        if (typeof data === "string") data = JSON.parse(data);
        if (data.error) throw new Error(data.error);
        setQrUrl(data.init_point);
      })
      .catch((err) => {
        setErrorMessage(err.message || "Error al generar el QR.");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [amount]);

  // Aquí está el return "estilo A":
  return (
    <div className="payment-qr-container">
      {loading ? (
        <p className="loading-message">Generando código QR...</p>
      ) : errorMessage ? (
        <p className="error-message">{errorMessage}</p>
      ) : (
        <div className="qr-content">
          {qrUrl ? (
            <QRCodeCanvas className="qr-canvas" value={qrUrl} size={256} />
          ) : (
            <p className="error-message">
              Ocurrió un error al generar el código QR.
            </p>
          )}
        </div>
      )}
    </div>
  );
};

export default PaymentQR;
