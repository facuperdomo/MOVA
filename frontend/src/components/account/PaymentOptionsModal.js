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
    cashBoxCode,
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
    const [payMethod, setPayMethod] = useState(null);

    const [showSuccess, setShowSuccess] = useState(false);
    const [successMessage, setSuccessMessage] = useState("");

    const [localItemPayments, setLocalItemPayments] = useState(itemPayments);
    const [unitItems, setUnitItems] = useState([]);

    // 2) Construimos `flatItems` a partir de `items`, marcando sólo las primeras `paidQtyMap.get(id)` unidades de cada línea
    const flatItems = React.useMemo(
        () => unitItems.map(u => ({
            id: u.itemId,
            productName: u.productName,
            price: u.unitPrice,
            ingredients: u.ingredients || [], // si lo envías
            quantity: 1,
            paid: u.paid,
        })),
        [unitItems]
    );

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
        const { total, remaining, share } = await getSplitStatus();
        const paidCount = total - remaining;
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

    useEffect(() => {
        if (step === "products") {
            const unpaid = flatItems.filter(i => !i.paid).length;
            if (unpaid === 0 && flatItems.length > 0) {
                setShowCloseConfirm(true);
            }
        }
    }, [flatItems, step]);

    useEffect(() => {
        if (step === "products") {
            (async () => {
                const flat = await customFetch(
                    `${API_URL}/api/accounts/${accountId}/unit-items`
                );
                setUnitItems(flat);
            })();
        }
    }, [step, accountId]);
    // ——————————————————————————————
    // 5bis) Pago “full” (total) (sin cerrar)
    // ——————————————————————————————
    const handleFullPay = async (method) => {
        // 1) Registro del pago total
        const orderDTO = await customFetch(
            `${API_URL}/api/accounts/${accountId}/payments/split`,
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    amount: amountToPay,
                    payerName: payerName || "–",
                    paymentMethod: method
                }),
            }
        );
        const fullPayload = {
            ...orderDTO,
            // items: vienen de la prop `items` o de `flatItems`
            items: items.map(i => ({
                productId: i.productId ?? i.id,
                name: i.productName,       // aquí sí le pones el nombre
                quantity: i.quantity,
                unitPrice: i.unitPrice ?? i.price,
                ingredientIds: i.ingredients?.map(x => x.id) || []
            }))
        };
        console.log("✅ Respuesta del backend al pagar:", fullPayload);
        // 2) Imprimimos el cierre de cuenta (FULL_CLOSURE mantiene tu ticket de cierre)
        await onPrint({ type: 'FULL_CLOSURE', payload: fullPayload });

        // 3) Ejecutamos callback “pago sin cerrar”
        onPaidWithoutClose();

        setShowCloseConfirm(true);

        // 4) Mostramos modal de éxito
        setSuccessMessage("Pago total realizado con éxito");
        setShowSuccess(true);
    };

    const handlePartialPay = async (method) => {
        // 1) Envías el pago
        const orderDTO = await customFetch(
            `${API_URL}/api/accounts/${accountId}/payments/split`,
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    amount: amountToPay,
                    payerName: payerName || "–",
                    paymentMethod: method,

                }),
            }
        );
        const fullPayload = {
            ...orderDTO,
            // items: vienen de la prop `items` o de `flatItems`
            items: items.map(i => ({
                productId: i.productId ?? i.id,
                name: i.productName,       // aquí sí le pones el nombre
                quantity: i.quantity,
                unitPrice: i.unitPrice ?? i.price,
                ingredientIds: i.ingredients?.map(x => x.id) || []
            }))
        };
        console.log("✅ Respuesta del backend al pagar:", fullPayload);
        // 2) Imprimes el ticket de pago parcial
        await onPrint({ type: 'PARTIAL_PAYMENT', payload: fullPayload });

        // 3) Lees el nuevo estado
        const { remaining } = await getSplitStatus();
        await fetchSplitStatus();

        // 4) Si ya no queda nadie → muestro confirm
        if (remaining === 0) {
            setShowCloseConfirm(true);
        }

        // 5) Muestro modal de éxito
        setSuccessMessage("Pago parcial realizado con éxito");
        setShowSuccess(true);
    };

    const getSplitStatus = async () => {
        const s = await customFetch(`${API_URL}/api/accounts/${accountId}/split/status`);
        return {
            total: Number(s.total),
            remaining: Number(s.remaining),
            share: Number(s.share),
        };
    };

    const handleProductsPay = async (method) => {
        const itemIdsToPay = selectedItems.map(idx => flatItems[idx]?.id).filter(Boolean);
        if (itemIdsToPay.length === 0) return;

        // 1) Envías el pago de ítems
        const orderDTO = await customFetch(
            `${API_URL}/api/accounts/${accountId}/payments/items/receipt`,
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ itemIds: itemIdsToPay, payerName: payerName || "–", paymentMethod: method }),
            }
        );

        // 2) Imprimes el ticket de pago de productos
        await onPrint({ type: 'PRODUCT_PAYMENT', payload: orderDTO });

        // 3) Espera a que el padre recargue items y pagos
        await onPaidWithoutClose();

        // 3) Después de onPaidWithoutClose(), recarga la lista real de pagos por ítem:
        const updated = await customFetch(
            `${API_URL}/api/accounts/${accountId}/items-with-payment`
        );
        // items-with-payment devuelve [{ itemId, quantity, paidQty }, …]
        setLocalItemPayments(updated);

        // 4) Ahora flatItems se actualiza correctamente, pues usa localItemPayments
        setSelectedItems([]);
        setAmountToPay(0);

        // 5) Y ya puedes preguntar si no queda nada pendiente:
        const remainingUnpaid = flatItems.filter(i => !i.paid).length;
        if (remainingUnpaid === 0) {
            setShowCloseConfirm(true);
        }

        // 6) Muestro modal de éxito
        setSuccessMessage("Pago de productos realizado con éxito");
        setShowSuccess(true);
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
    // ——————————————————————————————
    // 6) Handler genérico para “Pagar”
    // ——————————————————————————————
    const handlePay = async (method) => {
        if (step === "full") {
            // Antes: abríamos el modal de cierre.
            // Ahora: hacemos el pago total inmediato
            await handleFullPay(method);
            return;
        }

        if (step === "products") {
            await handleProductsPay(method);
            return;
        }

        if (step === "split") {
            await handlePartialPay(method);
            return;
        }
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
                    body: JSON.stringify({ amount: amountToPay, payerName: payerName || "–", paymentMethod: payMethod }),
                }
            );
            console.log("✅ Pago registrado:", paymentResponse);

            // 2) Cerrar la cuenta y recibir un OrderDTO
            console.log("🔸 Solicitando cierre de cuenta al endpoint /close");
            const orderDTO = await customFetch(
                `${API_URL}/api/accounts/${accountId}/close`,
                {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ code: cashBoxCode, paymentMethod: payMethod })
                },

            );
            console.log("✅ Respuesta de cierre (OrderDTO):", orderDTO);

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

    function ConfirmCloseModal({ onConfirm, onCancel }) {
        return (
            <div className="confirm-close-overlay-a">
                <div className="confirm-close-modal-a">
                    <h3>¿Deseas cerrar la cuenta?</h3>
                    <div className="close-confirm-buttons">
                        <button onClick={onConfirm}>Sí, cerrar cuenta</button>
                        <button onClick={onCancel}>No, dejarla abierta</button>
                    </div>
                </div>
            </div>
        );
    }

    function SuccessModal({ message, onClose }) {
        return (
            <div className="success-overlay">
                <div className="success-modal">
                    <h3>{message}</h3>
                    <button onClick={onClose}>OK</button>
                </div>
            </div>
        );
    }

    return (

        <div className="payment-modal" onClick={(e) => {
            // Si se hace clic fuera del contenido, cerrar el modal
            if (e.target.classList.contains("payment-modal")) {
                onClose();
            }
        }}>
            {showSuccess && (
                <SuccessModal
                    message={successMessage}
                    onClose={() => {
                        setShowSuccess(false);
                    }}
                />
            )}
            <div className="payment-modal-content">
                {showCloseConfirm && (
                    <ConfirmCloseModal
                        onConfirm={confirmCloseAccount}
                        onCancel={cancelCloseAccount}
                    />
                )}
                {!showCloseConfirm && (
                    <>
                        <div className="payment-modal-header-row">
                            {!showCloseConfirm && (
                                <div className="payment-modal-header-row">
                                    {step !== "choose" && (
                                        <button className="pm-back-btn" onClick={() => { setStep("choose"); setShowQR(false); }}>
                                            <ArrowLeft size={26} />
                                        </button>
                                    )}
                                    <h3 className="modal-title">
                                        {step === "split" && "Dividir cuenta"}
                                        {step === "full" && "Pagar todo"}
                                        {step === "products" && "Pagar productos"}
                                        {step === "choose" && "¿Cómo quieres pagar?"}
                                    </h3>
                                    <button className="popup-close-payment" onClick={onClose}>
                                        <X size={26} />
                                    </button>
                                </div>
                            )}
                        </div>

                        {/* — PASO 1: Elegir modalidad de pago — */}
                        {step === "choose" && (
                            <>
                                <button onClick={() => setStep("full")}>Pagar toda la cuenta</button>
                                <button onClick={() => setStep("split")}>Repartir entre...</button>
                                <button onClick={() => setStep("products")}>Pagar productos</button>
                            </>
                        )}

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

                                {amountToPay <= 0 ? (
                                    <button disabled>No hay saldo pendiente</button>
                                ) : (
                                    <>
                                        <button onClick={() => handlePay("CASH")}>Efectivo</button>
                                        <button onClick={() => { setPayMethod("QR"); setShowQR(true); }}>QR</button>
                                    </>
                                )}
                            </>
                        )}

                        {step === "full" && showCloseConfirm && (
                            <ConfirmCloseModal
                                onConfirm={confirmCloseAccount}
                                onCancel={cancelCloseAccount}
                            />
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
                                            const v = Number(e.target.value);
                                            setPeople(v > 0 ? v : 1);
                                        }}
                                        onBlur={() => handlePeopleChange(people)}
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

                                {!showCloseConfirm && (
                                    !showQR ? (
                                        <>
                                            <button onClick={() => handlePartialPay("CASH")}>Efectivo</button>
                                            <button onClick={() => { setPayMethod("QR"); setShowQR(true); }}>QR</button>
                                        </>
                                    ) : (
                                        <PaymentQR amount={share} />
                                    )
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
                                                        setSelectedItems(prev =>
                                                            prev.includes(idx)
                                                                ? prev.filter(x => x !== idx)
                                                                : [...prev, idx]
                                                        );
                                                    }}
                                                />
                                                {i.productName} = ${i.price.toFixed(2)}{" "}
                                                {i.paid && <span className="paid-tag">(Pagado)</span>}
                                            </label>
                                        </li>
                                    ))}
                                </ul>

                                <p>Subtotal: ${amountToPay.toFixed(2)}</p>

                                {showQR ? (
                                    <PaymentQR
                                        amount={amountToPay}
                                        onPaymentSuccess={() => handleProductsPay(payMethod)}
                                    />
                                ) : (
                                    <>
                                        <button
                                            onClick={() => handleProductsPay("CASH")}
                                            disabled={selectedItems.length === 0}
                                        >
                                            Efectivo
                                        </button>
                                        <button
                                            onClick={() => { setPayMethod("QR"); setShowQR(true); }}
                                            disabled={selectedItems.length === 0}
                                        >
                                            QR
                                        </button>
                                    </>
                                )}
                            </>
                        )}
                    </>
                )}
            </div>

        </div>
    );
}