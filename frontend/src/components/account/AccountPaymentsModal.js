// src/components/account/AccountPaymentsModal.js
import React, { useEffect, useState } from "react";
import { customFetch } from "../../utils/api";
import { X } from "lucide-react";
import "./accountPaymentsModalStyle.css";
import { API_URL } from "../../config/apiConfig";

export default function AccountPaymentsModal({ accountId, onClose }) {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 1) Al montar, traemos los pagos de la cuenta
  useEffect(() => {
    if (!accountId) return;
    (async () => {
      try {
        // Asumimos que existe un endpoint GET /api/accounts/{id}/payments
        const data = await customFetch(`${API_URL}/api/accounts/${accountId}/payments`);
        // data debería ser un array de { amount, payerName, paidAt, ... }
        setPayments(Array.isArray(data) ? data : []);
      } catch (err) {
        console.error("Error cargando pagos de cuenta:", err);
        setError("No se pudo cargar el historial de pagos.");
      } finally {
        setLoading(false);
      }
    })();
  }, [accountId]);

  return (
    <div className="account-payments-modal-overlay">
      <div className="account-payments-modal-content">
        {/* Cabecera con botón de cerrar */}
        <div className="modal-header">
          <h2>Historial de Pagos</h2>
          <button className="close-btn" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        {loading && <p>Cargando pagos...</p>}

        {error && <p className="error">{error}</p>}

        {!loading && !error && payments.length === 0 && (
          <p>No hay pagos registrados para esta cuenta.</p>
        )}

        {!loading && !error && payments.length > 0 && (
          <table className="payments-table">
            <thead>
              <tr>
                <th>Monto</th>
                <th>Pagador</th>
                <th>Fecha</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id || `${p.paidAt}-${p.amount}`}>
                  <td>${p.amount.toFixed(2)}</td>
                  <td>{p.payerName || "–"}</td>
                  <td>{new Date(p.paidAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
