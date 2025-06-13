// src/components/account/PaymentOptionsModal.js

import React, { useState, useEffect } from "react";
import { customFetch } from "../../utils/api";
import PaymentQR from "../paymentqr/PaymentQR";
import { API_URL } from "../../config/apiConfig";
import { ArrowLeft, X } from "lucide-react";
import "./paymentOptionsModalStyle.css";
import { useMemo } from "react";

export default function PaymentOptionsModal({
    accountId,
    items,
    // `itemPayments` puede venir como:
    // 1) [{ itemId: 123, quantity: 2, paidQty: 2 }, …]
    // 2) [123, 124, …]  (sólo IDs)
    itemPayments = [],
    // El total actual de la cuenta
    total: currentTotal,
    onClose,
    // Nuevas props:
    onPaidAndClose,     // se llama si paga todo y cierra la cuenta
    onPaidWithoutClose, // se llama si paga todo pero deja abierta
    // Estado del split que llega desde el padre:
    splitTotal,      // número total de porciones originalmente
    splitRemaining,  // cuántas porciones faltan
    paidMoney,       // cuánto se pagó ya (en pesos)
    onSplitUpdate,   // callback para cuando cambien “people”
    onPrint,
}) {
    const [step, setStep] = useState("choose");      // choose | full | split | products
    const [selectedItems, setSelectedItems] = useState([]); // índices de flatItems elegidos
    const [amountToPay, setAmountToPay] = useState(0);
    const [showQR, setShowQR] = useState(false);

    // Para el confirm “¿Cerrar cuenta después de este pago?”
    const [showCloseConfirm, setShowCloseConfirm] = useState(false);
    const [payerName, setPayerName] = useState("");
    const [people, setPeople] = useState(splitRemaining > 0 ? splitRemaining : splitTotal || 1);
    const [share, setShare] = useState(0);
    const [cyclePaidPeople, setCyclePaidPeople] = useState(0);
    const [totalPeople, setTotalPeople] = useState(splitTotal);

    const paidQtyMap = useMemo(() => {
        const m = new Map();
        itemPayments.forEach(ip => {
            const id = typeof ip === "number" ? ip : ip.itemId;
            const paidQty = ip.paidQuantity ?? ip.paidQty ?? (ip.paid ? ip.quantity : 0);
            m.set(id, paidQty);
        });
        return m;
    }, [itemPayments]);

    // 2) Construimos `flatItems` a partir de `items`, marcando sólo las primeras `paidQtyMap.get(id)` unidades de cada línea
    const flatItems = React.useMemo(() => {
        return items.flatMap(i => {
            const id = i.id || i.itemId;
            const qty = i.quantity;
            const paidQty = paidQtyMap.get(id) || 0;
            const arr = [];
            for (let idx = 0; idx < qty; idx++) {
                arr.push({
                    id,
                    productName: i.productName,
                    price: i.unitPrice ?? i.price ?? 0,
                    ingredients: i.ingredients || [],
                    quantity: 1,
                    paid: idx < paidQty,
                });
            }
            return arr;
        });
    }, [items, paidQtyMap]);

    // —————————————————————————————————————————————————————————————————————
    // 4) Si el paso es “products”, sumamos los precios de los índices que el usuario marcó
    // —————————————————————————————————————————————————————————————————————
    useEffect(() => {
        if (step === "products") {
            const sum = selectedItems.reduce((acc, idx) => {
                const it = flatItems[idx];
                return acc + (it.price || 0);
            }, 0);
            setAmountToPay(sum);
        }
    }, [step, selectedItems, flatItems]);


    const fetchSplitStatus = async () => {
        const s = await customFetch(`${API_URL}/api/accounts/${accountId}/split/status`);
        const total = Number(s.total);
        const remaining = Number(s.remaining);
        const paidCount = total - remaining;
        const share = Number(s.share);
        setPeople(remaining);
        setShare(share);
        setCyclePaidPeople(paidCount);
        setTotalPeople(total);
        setAmountToPay(share);
        // si necesitas, guarda currentTotal o paidMoney en estado
    };

    useEffect(() => {
        if (step === "split") {
            fetchSplitStatus();
        }
    }, [step]);

    useEffect(() => {
        if (step === "full") {
            const due = currentTotal - (paidMoney || 0);
            setAmountToPay(due);
        }
    }, [step, currentTotal, paidMoney]);

    const handlePartialPay = async () => {
        // 1) Envía el pago parcial y recibe un OrderDTO del backend
        const orderDTO = await customFetch(
            `${API_URL}/api/accounts/${accountId}/payments/split`,
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ amount: amountToPay, payerName: payerName || "–" }),
            }
        );

        // 2) Imprime usando ese OrderDTO
        await onPrint({ type: 'PARTIAL_PAYMENT', payload: orderDTO });

        // 3) Actualiza la UI
        onPaidWithoutClose();
        await fetchSplitStatus();
        onClose();
    };

    const handlePeopleChange = async (n) => {
        await customFetch(`${API_URL}/api/accounts/${accountId}/split?people=${n}`, { method: "PUT" });
        await fetchSplitStatus();
        onSplitUpdate?.(n);   // o los parámetros que prefieras
    };
    // —————————————————————————————————————————————————————————————————————
    // 6) Handler para “Pagar”:
    //    - Si step === “full” → primero mostramos confirmación de cerrar.
    //    - Si step === “products” → enviamos array de `itemIds` seleccionados.
    //    - Si step === “split” → enviamos sólo el monto parcial.
    // —————————————————————————————————————————————————————————————————————
    const handlePay = async () => {
        // — Paso “Pagar toda la cuenta” muestra el confirm —
        if (step === "full") {
            setShowCloseConfirm(true);
            return;
        }

        // — Paso “Pagar productos sueltos” —
        if (step === "products") {
            // 1) Lista de IDs a pagar
            const itemIdsToPay = selectedItems
                .map(idx => flatItems[idx]?.id)
                .filter(id => id != null);

            if (itemIdsToPay.length === 0) {
                alert("No seleccionaste ningún ítem válido para pagar.");
                return;
            }

            try {
                console.log("▶ handlePay (products): enviando IDs", itemIdsToPay);

                // 2) Llamada al endpoint que devuelve un OrderDTO
                const orderDTO = await customFetch(
                    `${API_URL}/api/accounts/${accountId}/payments/items/receipt`,
                    {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            itemIds: itemIdsToPay,
                            payerName: payerName || "–"
                        }),
                    }
                );
                console.log("✅ handlePay: OrderDTO recibido:", orderDTO);

                // 3) Imprimir sólo los ítems que pagaste
                await onPrint({ type: 'PRODUCT_PAYMENT', payload: orderDTO });
                console.log("✅ handlePay: impresión completada");

                // 4) Actualizar UI y cerrar modal
                onPaidWithoutClose();
                onClose();
            } catch (err) {
                console.error("❌ handlePay (products) error:", err);
                alert("Ocurrió un error al registrar el pago de productos.");
            }

            return;
        }

        // Si algún día agregas otro paso (QR, split, etc.), lo gestionas aquí…
    };

    // —————————————————————————————————————————————————————————————————————
    // 7) Confirmación “Sí, cerrar cuenta”:
    //    Registramos el pago completo y luego cerramos la cuenta en el backend.
    // —————————————————————————————————————————————————————————————————————
    const confirmCloseAccount = async () => {
        try {
            console.log("🔔 confirmCloseAccount: iniciando flujo de cierre completo");
            console.log("🔸 Payload de pago antes de cerrar:", { amount: amountToPay, payerName });

            // 1) Enviar el último pago parcial (si aplica)
            const paymentResponse = await customFetch(
                `${API_URL}/api/accounts/${accountId}/payments/split`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ amount: amountToPay, payerName: payerName || "–" }),
                }
            );
            console.log("✅ Pago registrado:", paymentResponse);

            // 2) Cerrar la cuenta y recibir un OrderDTO
            console.log("🔸 Solicitando cierre de cuenta al endpoint /close");
            const orderDTO = await customFetch(
                `${API_URL}/api/accounts/${accountId}/close`,
                { method: "PUT" }
            );
            console.log("✅ Respuesta de cierre (OrderDTO):", orderDTO);

            // 3) Imprimir el ticket de cierre
            console.log("🔸 Enviando a impresión:", orderDTO);
            await onPrint({ type: 'FULL_CLOSURE', payload: orderDTO });
            console.log("✅ onPrint completado");

            // 4) Limpieza de UI
            console.log("🔸 Actualizando UI tras cierre");
            onPaidAndClose();
            onClose();
            console.log("✅ confirmCloseAccount: flujo finalizado");
        } catch (err) {
            console.error("❌ Error en confirmCloseAccount:", err);
            alert("Ocurrió un error al cerrar la cuenta.");
            onPaidAndClose();
            onClose();
        }
    };

    // —————————————————————————————————————————————————————————————————————
    // 8) “No, dejarla abierta” tras pagar todo:
    //    Registramos el pago y no cerramos la cuenta.
    // —————————————————————————————————————————————————————————————————————
    const cancelCloseAccount = async () => {
        try {
            await customFetch(`${API_URL}/api/accounts/${accountId}/payments/split`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ amount: amountToPay, payerName: payerName || "–" }),
            });
        } catch (err) {
            console.error("Error registrando pago sin cerrar cuenta:", err);
            alert("Ocurrió un error al registrar el pago.");
        }
        onPaidWithoutClose();
        onClose();
    };

    return (
        <div className="payment-modal">
            <div className="payment-modal-content">
                <div className="payment-modal-header">
                    {step !== "choose" && !showCloseConfirm && (
                        <button
                            className="pm-back-btn"
                            onClick={() => {
                                setStep("choose");
                                setShowQR(false);
                            }}
                        >
                            <ArrowLeft size={20} />
                        </button>
                    )}
                    <button className="pm-close-btn" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>

                {/* — PASO 1: Elegir modalidad de pago — */}
                {step === "choose" && (
                    <>
                        <h2>¿Cómo quieres pagar?</h2>
                        <button onClick={() => setStep("full")}>Pagar toda la cuenta</button>
                        <button onClick={() => setStep("split")}>Repartir entre...</button>
                        <button onClick={() => setStep("products")}>Pagar productos</button>
                        <button onClick={onClose}>Cancelar</button>
                    </>
                )}

                {/* — PASO 2: “Pagar toda la cuenta” — */}
                {step === "full" && !showCloseConfirm && (
                    <>
                        <h3>Importe restante: ${amountToPay.toFixed(2)}</h3>
                        <label className="payer-name-input">
                            Nombre del pagador:
                            <input
                                type="text"
                                value={payerName}
                                onChange={(e) => setPayerName(e.target.value)}
                                placeholder="Ej. Juan Pérez"
                            />
                        </label>
                        {!showQR ? (
                            <>
                                <button onClick={() => handlePay("CASH")}>Efectivo</button>
                                <button onClick={() => setShowQR(true)}>QR</button>
                            </>
                        ) : (
                            <PaymentQR amount={amountToPay} />
                        )}
                    </>
                )}

                {/* — CONFIRMATORIO: “¿Cerrar cuenta después de este pago?” — */}
                {showCloseConfirm && (
                    <div className="close-confirm-container">
                        <h3>¿Deseas cerrar la cuenta después de este pago?</h3>
                        <div className="close-confirm-buttons">
                            <button onClick={confirmCloseAccount}>Sí, cerrar cuenta</button>
                            <button onClick={cancelCloseAccount}>No, dejarla abierta</button>
                        </div>
                    </div>
                )}

                {/* — PASO 3: “Repartir entre ‘people’ personas” — */}
                {step === "split" && (
                    <>
                        <label className="payer-name-input">
                            Personas:
                            <input
                                type="number"
                                min={1}
                                value={people}
                                onChange={e => {
                                    // actualiza solo el state local
                                    const v = Number(e.target.value);
                                    setPeople(v > 0 ? v : 1);
                                }}
                                onBlur={() => {
                                    // cuando el usuario sale del campo, confirmamos el cambio
                                    handlePeopleChange(people);
                                }}
                            />
                        </label>

                        <p>Cada uno paga: ${share.toFixed(2)}</p>
                        <p>Pagadas: <strong>{cyclePaidPeople}</strong> / <strong>{totalPeople}</strong> personas</p>

                        <label className="payer-name-input">
                            Nombre del pagador:
                            <input
                                type="text"
                                value={payerName}
                                onChange={e => setPayerName(e.target.value)}
                                placeholder="Ej. Ana López"
                            />
                        </label>

                        {!showQR ? (
                            <>
                                <button onClick={handlePartialPay}>Efectivo</button>
                                <button onClick={() => setShowQR(true)}>QR</button>
                            </>
                        ) : (
                            <PaymentQR amount={share} />
                        )}
                    </>
                )}

                {/* — PASO 4: “Pagar productos sueltos” — */}
                {step === "products" && (
                    <>
                        <label className="payer-name-input">
                            Nombre del pagador:
                            <input
                                type="text"
                                value={payerName}
                                onChange={e => setPayerName(e.target.value)}
                                placeholder="Ej. Juan Pérez"
                            />
                        </label>
                        <ul className="pm-product-list">
                            {flatItems.map((i, idx) => (
                                <li key={`${i.id}-${idx}`}>
                                    <label>
                                        <input
                                            type="checkbox"
                                            checked={i.paid || selectedItems.includes(idx)}
                                            disabled={i.paid}
                                            onChange={() => {
                                                if (i.paid) return;
                                                setSelectedItems((prev) =>
                                                    prev.includes(idx)
                                                        ? prev.filter((x) => x !== idx)
                                                        : [...prev, idx]
                                                );
                                            }}
                                        />
                                        {i.productName} = ${i.price.toFixed(2)}{" "}
                                        {i.paid && (
                                            <span style={{ color: "#4CAF50", marginLeft: "8px" }}>
                                                (Pagado)
                                            </span>
                                        )}
                                    </label>
                                </li>
                            ))}
                        </ul>
                        <p>Subtotal: ${amountToPay.toFixed(2)}</p>
                        {!showQR ? (() => {
                            // cuántas unidades quedan sin pagar
                            const unpaidCount = flatItems.filter(x => !x.paid).length;
                            const isLastBatch = selectedItems.length === unpaidCount;
                            return (
                                <>
                                    <button
                                        onClick={() => {
                                            if (isLastBatch) {
                                                // si paga todo, voy al confirm de cerrar
                                                setShowCloseConfirm(true);
                                            } else {
                                                handlePay("CASH");
                                            }
                                        }}
                                        disabled={selectedItems.length === 0}
                                    >
                                        Efectivo
                                    </button>
                                    <button
                                        onClick={() => {
                                            if (isLastBatch) {
                                                setShowCloseConfirm(true);
                                            } else {
                                                setShowQR(true);
                                            }
                                        }}
                                        disabled={selectedItems.length === 0}
                                    >
                                        QR
                                    </button>
                                </>
                            );
                        })() : (
                            <PaymentQR
                                amount={amountToPay}
                                onPaymentSuccess={() => handlePay()}
                            />
                        )}
                    </>
                )}
            </div>
        </div>
    );
}