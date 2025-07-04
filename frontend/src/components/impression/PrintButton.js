import React, { useState } from "react";
import { printOrder } from "../../utils/print";

export default function PrintButton({ order, onError, onSuccess }) {
  const [loading, setLoading] = useState(false);

  const handlePrint = async () => {
    setLoading(true);
    try {
      await printOrder(order);
      // Llama al callback de éxito si se provee
      if (typeof onSuccess === 'function') {
        onSuccess();
      }
    } catch (err) {
      console.error("Error imprimiendo ticket:", err);
      // Llama al callback de error para mostrar modal
      if (typeof onError === 'function') {
        onError(err);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <button onClick={handlePrint} disabled={loading} className="popup-btn">
      {loading ? "Imprimiendo…" : "Imprimir Ticket"}
    </button>
  );
}
