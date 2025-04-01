import { API_URL } from "../config/apiConfig";

export const customFetch = async (url, options = {}) => {
  let token = localStorage.getItem("token");

  // Si la URL es de MercadoPago, no adjuntar el header Authorization
  const isMercadoPagoEndpoint = url.includes("/api/mercadopago/");

  const headers = {
    "Content-Type": "application/json",
    // Solo se agrega Authorization si NO es un endpoint de MercadoPago
    ...(!isMercadoPagoEndpoint && token ? { Authorization: `Bearer ${token}` } : {}),
  };

  try {
    let response = await fetch(url, { ...options, headers });

    // Solo intentamos refrescar el token si NO es un endpoint de MercadoPago
    if (!isMercadoPagoEndpoint && response.status === 401) {
      console.warn("⚠️ Token expirado. Intentando refrescar...");
      const newToken = await refreshToken();
      if (!newToken) {
        console.error("❌ No se pudo refrescar el token. Redirigiendo al login...");
        localStorage.removeItem("token");
        localStorage.removeItem("isAdmin");
        window.location.href = "/login";
        return Promise.reject(new Error("Sesión expirada. Redirigiendo al login."));
      }
      headers.Authorization = `Bearer ${newToken}`;
      response = await fetch(url, { ...options, headers });
    }

    const contentType = response.headers.get("Content-Type");
    if (contentType && contentType.includes("application/json")) {
      return response.json();
    } else {
      return response.text();
    }
  } catch (error) {
    console.error("❌ Error en la solicitud:", error);
    return Promise.reject(error);
  }
};

/**
 * Función para refrescar el token llamando al backend.
 */
const refreshToken = async () => {
  const token = localStorage.getItem("token");
  if (!token) return null;

  try {
    const response = await fetch(`${API_URL}/auth/refresh-token`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      console.error("❌ Error al refrescar el token:", await response.text());
      return null;
    }

    const data = await response.json();
    const newToken = data.newToken;
    localStorage.setItem("token", newToken);
    console.log("✅ Token refrescado correctamente.");
    return newToken;
  } catch (error) {
    console.error("❌ Error en la solicitud de refresh token:", error);
    return null;
  }
};
