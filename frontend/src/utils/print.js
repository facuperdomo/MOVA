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

/**
 * Imprime **solo** los items que vengan en el OrderDTO (payload completo)
 * usando el endpoint /api/print/direct/receipt/items.
 */
export async function printItemsReceipt(orderDto, printerId = null) {
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
  if (printerId) {
    headers["X-Printer-Id"] = printerId;
  }

  const resp = await fetch(
    `${API_URL}/api/print/direct/receipt/items`,
    {
      method:  "POST",
      headers,
      body:    JSON.stringify(orderDto)
    }
  );

  if (!resp.ok) {
    const texto = await resp.text();
    throw new Error(`Error ${resp.status}: ${texto}`);
  }
}