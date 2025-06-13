// src/components/account/AccountsListModal.js

import React from "react";
import { X } from "lucide-react";
import "./accountsListModalStyle.css";

/**
 * Props esperados:
 * - accounts: array de cuentas abiertas
 * - selectedAccountId: ID de la cuenta actualmente seleccionada (para resaltar)
 * - onClose: función para ocultar este modal
 * - onSelectAccount: callback al hacer click en el nombre de la cuenta (seleccionarla)
 * - onShowDetails: callback para el botón “Info” (abre modal de detalle)
 * - onShowPayments: callback para el botón “Pagos” (abre AccountPaymentsModal)
 * - onPayAccount: callback para el botón “Pagar” (abre PaymentOptionsModal)
 * - onCreateNew: callback para el botón “+” (crea nueva cuenta)
 * - onCloseAccount: callback para el botón “Cerrar” (intenta cerrar la cuenta inmediatamente)
 */
export default function AccountsListModal({
    accounts,
    selectedAccountId,
    onClose,
    onSelectAccount,
    onShowDetails,
    onShowPayments,
    onPayAccount,
    onCreateNew,
    onCloseAccount,
}) {
    return (
        <div className="accounts-modal">
            <div
                className="popup-overlay"
                onClick={(e) => {
                    if (e.target.classList.contains("popup-overlay")) {
                        onClose();
                    }
                }}
            >
                <div className="popup-content">
                    <X className="popup-close" size={32} onClick={onClose} />
                    <h2>Cuentas Abiertas</h2>
                    <button className="add-mesa-button" onClick={onCreateNew}>
                        +
                    </button>

                    <div className="account-list">
                        {accounts.map((acc) => (
                            <div
                                key={acc.id}
                                className={`account-item ${acc.remainingMoney > 0 ? "pendiente" : "pagada"} ${selectedAccountId === acc.id ? "selected" : ""}`}
                                style={{ cursor: "pointer" }}
                            >
                                {/* Nombre de la mesa / cuenta */}
                                <div
                                    className="table-name"
                                    onClick={() => {
                                        onSelectAccount(acc.id);
                                        onClose();
                                    }}
                                >
                                    {acc.name} (#{acc.id})
                                </div>

                                {/* Contenedor de botones en 2 columnas */}
                                <div className="account-buttons">
                                    {/* “Info” abre el modal de detalle completo */}
                                    <button
                                        className="more-info-btn"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            onShowDetails(acc);
                                        }}
                                    >
                                        Info
                                    </button>

                                    {/* “Pagos” abre el modal de historial de pagos */}
                                    <button
                                        className="payments-btn"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            onShowPayments(acc.id);
                                        }}
                                    >
                                        Pagos
                                    </button>

                                    {/* “Pagar” abre el modal de opciones de pago */}
                                    <button
                                        className="pay-account-btn"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            onPayAccount(acc.id);
                                        }}
                                    >
                                        Pagar
                                    </button>

                                    {/* “Cerrar” cierra la cuenta (o abre modal de pago si queda saldo) */}
                                    <button
                                        className="close-account-btn"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            onCloseAccount(acc.id);
                                        }}
                                    >
                                        Cerrar
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
