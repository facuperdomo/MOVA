import React, { useEffect, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { API_URL } from "../../config/apiConfig";
import "./paymentQRStyle.css";
import { customFetch } from "../../utils/api";

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    console.log("PaymentQR: iniciando fetch para monto:", amount);
    const companyId = localStorage.getItem("companyId");
    console.log("CompanyID:", companyId);
    if (!companyId) {
      console.error("No se encontró companyId en localStorage.");
      setErrorMessage("No se encontró la identificación de la empresa.");
      setLoading(false);
      return;
    }

    // Construir la URL incluyendo el companyId
    const url = `${API_URL}/api/mercadopago/create-preference/${companyId}`;

    customFetch(url, {
      method: "POST",
      body: JSON.stringify({ amount }),
    })
      .then((data) => {
        // Si data es un string, intentar parsearlo
        if (typeof data === "string") {
          try {
            data = JSON.parse(data);
          } catch (e) {
            throw new Error("Error al parsear la respuesta del servidor.");
          }
        }
        if (data.error) {
          throw new Error(data.error);
        }
        console.log("PaymentQR: datos recibidos:", data);
        setQrUrl(data.init_point);
        setLoading(false);
      })
      .catch((error) => {
        console.error("PaymentQR: Error creando la preferencia:", error);
        setErrorMessage(error.message);
        setLoading(false);
      });
  }, [amount]);

  return (
    <div className="payment-qr-container">
      {loading ? (
        <p className="loading-message">Generando código QR...</p>
      ) : errorMessage ? (
        <p className="error-message">{errorMessage}</p>
      ) : (
        <div className="qr-content">
          <p className="qr-instruction">Escanea el código QR para pagar:</p>
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
