import React, { useEffect, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { customFetch } from "../../utils/api";
import "./paymentQRStyle.css";

const PaymentQR = ({ amount }) => {
  const [qrUrl, setQrUrl]       = useState("");
  const [loading, setLoading]   = useState(true);
  const [errorMessage, setError] = useState("");

  useEffect(() => {
    const branchId = localStorage.getItem("branchId");
    console.log("🏷 branchId desde localStorage:", branchId);

    if (!branchId) {
      setError("No se encontró branchId");
      setLoading(false);
      return;
    }

    customFetch(`/api/mercadopago/create-preference/${branchId}`, {
      method: "POST",
      body: JSON.stringify({ amount })
    })
      .then((resp) => {
        console.log("📲 create-preference response:", resp);
        if (resp.error) throw new Error(resp.error);
        setQrUrl(resp.init_point);
      })
      .catch((err) => {
        console.error("💥 Error generando QR:", err);
        setError(err.message || "Error al generar QR");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [amount]);

  return (
    <div className="payment-qr-container">
      {loading
        ? <p>Generando código QR…</p>
        : errorMessage
          ? <p className="error">{errorMessage}</p>
          : qrUrl
            ? <QRCodeCanvas value={qrUrl} size={256} />
            : <p className="error">No vino init_point en la respuesta</p>
      }
    </div>
  );
};

export default PaymentQR;
