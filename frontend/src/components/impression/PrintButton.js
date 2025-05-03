// src/components/PrintButton.jsx
import React, { useState } from 'react';

export default function PrintButton({ order }) {
  const [loading, setLoading] = useState(false);

  const handlePrint = async () => {
    setLoading(true);
    try {
      const resp = await fetch('/api/print', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(order)
      });
      if (resp.ok) {
        alert('✅ Ticket enviado a imprimir');
      } else {
        const text = await resp.text();
        alert(`❌ Error ${resp.status}: ${text}`);
      }
    } catch (e) {
      console.error(e);
      alert('❌ Error de red al enviar a imprimir');
    } finally {
      setLoading(false);
    }
  };

  return (
    <button onClick={handlePrint} disabled={loading}>
      {loading ? 'Imprimiendo…' : 'Imprimir Ticket'}
    </button>
  );
}
