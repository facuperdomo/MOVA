import React, { useEffect, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { API_URL } from "../../config/apiConfig";
import { customFetch } from "../../utils/api";
import "./paymentQRStyle.css";

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const companyId = localStorage.getItem("companyId");
    if (!companyId) {
      setErrorMessage("No se encontró la identificación de la empresa.");
      setLoading(false);
      return;
    }

    customFetch(
      `${API_URL}/api/mercadopago/create-preference/${companyId}`,
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

  if (loading) return <p className="loading-message">Generando código QR…</p>;
  if (errorMessage) return <p className="error-message">{errorMessage}</p>;

  return qrUrl ? (
    <div className="qr-content">
      <QRCodeCanvas value={qrUrl} size={256} />
    </div>
  ) : (
    <p className="error-message">Ocurrió un error al generar el código QR.</p>
  );
};

export default PaymentQR;
