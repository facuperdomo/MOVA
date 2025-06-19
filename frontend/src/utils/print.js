// src/utils/print.js
import { API_URL } from "../config/apiConfig";

export async function printOrder(order, printerId = null) {
  console.log("▶ Enviando impresión, payload:", order, "printerId:", printerId);
  const token = localStorage.getItem("token");
  const branchId = localStorage.getItem("branchId");

  if (!branchId) {
    throw new Error("No se encontró el branchId en localStorage");
  }

  const headers = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${token}`,
    "X-Branch-Id": branchId
  };

  if (printerId) {
    headers["X-Printer-Id"] = printerId;
  }

  const payload = {
    id: order.id,
    totalAmount: order.totalAmount,
    paymentMethod: order.paymentMethod
  };
  const resp = await fetch(`${API_URL}/api/print/direct`, {
    method: "POST",
    headers,
    body: JSON.stringify(payload)
  });

  if (!resp.ok) {
    const texto = await resp.text();
    throw new Error(`Error ${resp.status}: ${texto}`);
  }
}

/**
 * Imprime **solo** los items que vienen en el DTO completo,
 * usando el endpoint /api/print/direct/receipt/items.
 */
export async function printItemsReceipt(orderDto, printerId = null) {
  console.log("▶ Enviando impresión, payload:", orderDto, "printerId:", printerId);
  const token = localStorage.getItem("token");
  const branchId = localStorage.getItem("branchId");

  if (!branchId) {
    throw new Error("No se encontró el branchId en localStorage");
  }

  const headers = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${token}`,
    "X-Branch-Id": branchId
  };

  if (printerId) {
    headers["X-Printer-Id"] = printerId;
  }

  const resp = await fetch(
    `${API_URL}/api/print/direct/receipt/items`,
    {
      method: "POST",
      headers,
      body: JSON.stringify(orderDto)
    }
  );

  if (!resp.ok) {
    const texto = await resp.text();
    throw new Error(`Error ${resp.status}: ${texto}`);
  }
}
