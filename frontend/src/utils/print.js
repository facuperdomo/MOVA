// src/utils/print.js
import { API_URL } from "../config/apiConfig";

async function ensureDeviceId() {
  const deviceId = localStorage.getItem("deviceId");
  if (!deviceId) {
    throw new Error("No se encontró el deviceId en localStorage");
  }
  return deviceId;
}

export async function printOrder(order) {
  console.log("▶ [printOrder] payload recibido:", order, JSON.stringify(order, null, 2));
  const token = localStorage.getItem("token");
  const branchId = localStorage.getItem("branchId");
  const deviceId = await ensureDeviceId();

  if (!branchId) {
    throw new Error("No se encontró el branchId en localStorage");
  }

  const headers = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${token}`,
    "X-Branch-Id": branchId,
    "X-Device-Id": deviceId
  };

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
    // Intentamos parsear JSON de error
    let body;
    try {
      body = await resp.json();
    } catch {
      // si no es JSON, caemos al texto genérico
      const txt = await resp.text();
      throw new Error(txt || `Error ${resp.status}`);
    }
    // Lanzamos solo el message
    throw new Error(body.message || `Error ${resp.status}`);
  }
}

export async function printItemsReceipt(orderDto) {
  console.log("LO QUE SE ESTA MANDANDO EN PRINTITEMSRECIPT:" + orderDto, orderDto);
  const token = localStorage.getItem("token");
  const branchId = localStorage.getItem("branchId");
  const deviceId = localStorage.getItem("deviceId");

  if (!branchId) {
    throw new Error("No se encontró el branchId en localStorage");
  }
  if (!deviceId) {
    throw new Error("No se encontró el deviceId en localStorage");
  }

  const headers = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${token}`,
    "X-Branch-Id": branchId,
    "X-Device-Id": deviceId
  };

  const resp = await fetch(`${API_URL}/api/print/direct/receipt/items`, {
    method: "POST",
    headers,
    body: JSON.stringify(orderDto)
  });

  if (!resp.ok) {
    // Intentamos parsear JSON de error
    let body;
    try {
      body = await resp.json();
    } catch {
      // si no es JSON, caemos al texto genérico
      const txt = await resp.text();
      throw new Error(txt || `Error ${resp.status}`);
    }
    // Lanzamos solo el message
    throw new Error(body.message || `Error ${resp.status}`);
  }
}
