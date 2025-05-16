// src/components/impression/PrintButton.jsx
import React, { useState } from "react";
import { printOrder } from "../../utils/print";

export default function PrintButton({ order }) {
  const [loading, setLoading] = useState(false);

  const handlePrint = async () => {
    setLoading(true);
    try {
      await printOrder(order);
      alert("✅ Ticket enviado a imprimir");
    } catch (e) {
      console.error(e);
      alert("❌ " + e.message);
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
