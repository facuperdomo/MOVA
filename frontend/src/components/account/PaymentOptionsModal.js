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
    pushNotification,
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

    const [unitItems, setUnitItems] = useState([]);

    const [processing, setProcessing] = useState(false);

    useEffect(() => {
        if (step === "products") {
            // 1) Reseteo estado UI
            setShowCloseConfirm(false);
            setSelectedItems([]);

            // 2) Traigo SIEMPRE el estado más actual
            (async () => {
                const flat = await customFetch(
                    `${API_URL}/api/accounts/${accountId}/unit-items`
                );
                console.log("🧾 [LOG] unit-items crudos del server:", flat);
                const withUnitId = flat.map((u, idx) => ({
                    ...u,
                    unitId: `${u.itemId}-${idx}`
                }));
                console.table(withUnitId.map(u => ({ unitId: u.unitId, paid: u.paid })));
                setUnitItems(withUnitId);
            })();
        }
    }, [step, accountId, items]);

    const flatItems = useMemo(
        () => unitItems.map(u => ({
            unitId: u.unitId,
            id: u.itemId,
            productName: u.productName,
            price: u.unitPrice,
            quantity: 1,
            paid: u.paid,
        })),
        [unitItems]
    )

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

    // Marca todos los unit-items como pagados sin generar pago/recibo
    const markAllPaid = async () => {
      try {
        await customFetch(
          `${API_URL}/api/accounts/${accountId}/unit-items/mark-all-paid`,
          { method: "PUT" }
        );
      } catch (err) {
        console.error("Error marcando todos los ítems como pagados:", err);
      }
    };

    // ——————————————————————————————
    // 5bis) Pago “full” (total) (sin cerrar)
    // ——————————————————————————————
    const handleFullPay = async (method) => {
        if (processing) return;
        setProcessing(true);
        // Si no hay nada que pagar, salimos sin crear pago de “0”
        if (amountToPay <= 0) {
            // Opcionalmente puedes cerrar aquí:
            onPaidWithoutClose();
            onClose();
            return;
        }
        const unitList = await customFetch(
            `${API_URL}/api/accounts/${accountId}/unit-items`
        );
        const unpaidItemIds = unitList
            .filter(u => !u.paid)
            .map(u => u.itemId);

        let orderDTO = { items: [], totalAmount: 0 }; // fallback mínimo
        try {
            if (unpaidItemIds.length > 0) {
                orderDTO = await customFetch(
                    `${API_URL}/api/accounts/${accountId}/payments/items/receipt`,
                    {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            itemIds: unpaidItemIds,
                            payerName: payerName || "–",
                            paymentMethod: method
                        }),
                    }
                );
            } else {
                console.log("✅ Todos los ítems ya estaban pagados");
            }

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
            pushNotification('Pago Registrado', 'success');
            console.log("✅ Respuesta del backend al pagar:", fullPayload);
            // 2) Imprimimos el cierre de cuenta (FULL_CLOSURE mantiene tu ticket de cierre)
            await onPrint({ type: 'FULL_CLOSURE', payload: fullPayload });

            // 3) Ejecutamos callback “pago sin cerrar”
            onPaidWithoutClose();

            // 4) Marcamos TODOS los unit-items como pagados
            await markAllPaid();

            setShowCloseConfirm(true);
        } catch {
            pushNotification('Error al cerrar mesa', 'error');
        } finally {
            setProcessing(false);
        }
    };

    const handlePartialPay = async (method) => {
        if (people <= 0) {
            alert("La cantidad de personas debe ser mayor a 0 para dividir la cuenta.");
            return;
        }

        // 1) Envías el pago “split”
        await customFetch(
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

        // 2) Refrescas el estado del split
        const status = await customFetch(
            `${API_URL}/api/accounts/${accountId}/split/status`
        );
        const total = Number(status.total);
        const remaining = Number(status.remaining);
        const share = Number(status.share);
        const paidCount = total - remaining;

        setPeople(remaining);
        setShare(share);
        setCyclePaidPeople(paidCount);
        setTotalPeople(total);
        setAmountToPay(share);

        // 3) Si ya no queda nadie → marcamos todos los ítems como pagados
        if (remaining === 0) {
            await markAllPaid();
            setShowCloseConfirm(true);
        }

        // 4) Actualizo el listado de unidades con su nuevo flag `paid`
        const freshUnits = await customFetch(
            `${API_URL}/api/accounts/${accountId}/unit-items`
        );
        setUnitItems(
            freshUnits.map((u, idx) => ({
                ...u,
                unitId: `${u.itemId}-${idx}`
            }))
        );

        // 5) Mensaje de éxito
        pushNotification('Pago Realizado', 'success');
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
        console.log("💥 [LOG] handleProductsPay, selectedItems indices:", selectedItems);
        console.log("💥 [LOG] flatItems actuales:", flatItems)
        const counts = selectedItems.reduce((acc, idx) => {
            // ▶️ usa flatItems[idx].id, que es donde guardaste el itemId
            const id = flatItems[idx].id;
            acc[id] = (acc[id] || 0) + 1;
            return acc;
        }, {});
        // 2) Reconstruye el array duplicando cada id según su count
        const itemIdsToPay = Object.entries(counts)
            .flatMap(([id, cnt]) => Array(cnt).fill(Number(id)));
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
        console.log("✅ [LOG] respuesta pay-items/receipt:", orderDTO);
        // 2) Imprimes el ticket de pago de productos
        await onPrint({ type: 'PRODUCT_PAYMENT', payload: orderDTO });

        // 3) Espera a que el padre recargue items y pagos
        await onPaidWithoutClose();

        const raw = await customFetch(
            `${API_URL}/api/accounts/${accountId}/unit-items`
        );
        const withUnitId = raw.map((u, idx) => ({
            ...u,
            unitId: `${u.itemId}-${idx}`
        }));
        setUnitItems(withUnitId);

        // 4) Ahora flatItems se actualiza correctamente, pues usa localItemPayments
        setSelectedItems([]);
        setAmountToPay(0);

        // 5) Y ya puedes preguntar si no queda nada pendiente:
        const remainingUnpaid = flatItems.filter(i => !i.paid).length;
        if (remainingUnpaid === 0) {
            setShowCloseConfirm(true);
        }
    };

    const handlePeopleChange = async (n) => {
        // 1) actualizas en el back
        await customFetch(`${API_URL}/api/accounts/${accountId}/split?people=${n}`, { method: "PUT" });

        // 2) traes el estado completo de split
        const status = await customFetch(`${API_URL}/api/accounts/${accountId}/split/status`);
        // lo desestructuras:
        const total = Number(status.total);
        const remaining = Number(status.remaining);
        const share = Number(status.share);
        const paidMoney = Number(status.paidMoney || 0);
        const currentTotal = Number(status.currentTotal || 0);
        const itemPayments = status.itemPayments || [];

        // 3) notificas al padre con TODO
        onSplitUpdate?.(total, remaining, paidMoney, currentTotal, itemPayments);

        // 4) y actualizas aquí también tu propio share/people/amountToPay si quieres:
        setPeople(remaining);
        setShare(share);
        setAmountToPay(share);
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
        if (processing) return;
        if (step === "full") {
            // Antes: abríamos el modal de cierre.
            // Ahora: hacemos el pago total inmediato
            await handleFullPay(method);
            return;
        }

        if (step === "products") {
            setProcessing(true);
            await handleProductsPay(method);
            setProcessing(false);
            return;
        }

        if (step === "split") {
            setProcessing(true);
            await handlePartialPay(method);
            setProcessing(false);
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
                                        <button
                                            onClick={() => handlePay("CASH")}
                                        >
                                            {processing ? 'Procesando…' : 'Efectivo'}
                                        </button>
                                        {!showQR ? (
                                            <button
                                                onClick={() => { setPayMethod("QR"); setShowQR(true); }}
                                                disabled={processing || amountToPay <= 0}
                                            >
                                                QR
                                            </button>
                                        ) : (
                                            <PaymentQR
                                                amount={amountToPay}
                                                onPaymentSuccess={() => {
                                                    handleFullPay(payMethod);
                                                    setShowQR(false);      // oculta el QR
                                                    setStep("choose");     // revuelve al menu inicial
                                                }}
                                            />
                                        )}
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
                                        min={0}
                                        value={people}
                                        onChange={e => {
                                            const v = Number(e.target.value);
                                            setPeople(Number.isNaN(v) ? 0 : v < 0 ? 0 : v);
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
                                            <button
                                                onClick={() => handlePartialPay("CASH")}
                                                disabled={people <= 0}
                                            >Efectivo</button>
                                            <button
                                                onClick={() => { setPayMethod("QR"); setShowQR(true); }}
                                                disabled={people <= 0}
                                            >QR</button>
                                        </>
                                    ) : (
                                        <PaymentQR
                                            amount={share}
                                            onPaymentSuccess={() => {
                                                // primero registra el pago parcial
                                                handlePartialPay(payMethod);
                                                // luego resetea la vista
                                                setShowQR(false);
                                                setStep("choose");
                                            }}
                                        />
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
                                        <li key={i.unitId}>
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
                                        onPaymentSuccess={() => {
                                            handleProductsPay(payMethod);
                                            setShowQR(false);      // oculta el QR
                                            setStep("choose");     // revuelve al menu inicial
                                        }}
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