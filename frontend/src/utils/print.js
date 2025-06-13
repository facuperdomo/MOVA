// src/utils/print.js
import { API_URL } from "../config/apiConfig";

export async function printOrder(order, printerId = null) {
  const token    = localStorage.getItem("token");
  const branchId = localStorage.getItem("branchId");

  if (!branchId) {
    throw new Error("No se encontró el branchId en localStorage");
  }

  const headers = {
    "Content-Type":  "application/json",
    "Authorization": `Bearer ${token}`,
    "X-Branch-Id":   branchId
  };

  // Si se pasa un printerId, lo añadimos a la cabecera
  if (printerId) {
    headers["X-Printer-Id"] = printerId;
  }

  const resp = await fetch(`${API_URL}/api/print/direct`, {
    method:  "POST",
    headers,
    body:    JSON.stringify({
      id:          order.id,
      totalAmount: order.totalAmount
    })
  });

  if (!resp.ok) {
    const texto = await resp.text();
    throw new Error(`Error ${resp.status}: ${texto}`);
  }
}

export const printPartialOrder = async ({ items, amount, payerName }) => {
  // construye un objeto parecido a SaleDTO, pero marca como “pago parcial”
  const fakeSale = {
    dateTime: new Date().toISOString(),
    totalAmount: amount,
    items: items.map(i => ({
      productId:   i.productId,
      quantity:    i.quantity,
      unitPrice:   i.price,
      subTotal:    (i.price * i.quantity),
      paidPartial: true
    })),
    metadata: { payerName, partial: true }
  };
  return printOrder(fakeSale);
};

export const printProductOrder = async ({ items, amount, payerName }) => {
  const fakeSale = {
    dateTime: new Date().toISOString(),
    totalAmount: amount,
    items: items.map(i => ({
      productId: i.id,
      quantity:  i.quantity,
      unitPrice: i.price,
      subTotal:  i.price * i.quantity
    })),
    metadata: { payerName, productOnly: true }
  };
  return printOrder(fakeSale);
};