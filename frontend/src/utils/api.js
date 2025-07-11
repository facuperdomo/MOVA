// src/utils/api.js
import { API_URL } from "../config/apiConfig";

/** Si el token está a punto de expirar, intenta un refresh proactivo */
async function maybeRefreshToken() {
  const raw = localStorage.getItem("token");
  if (!raw) return null;
  const token = raw.startsWith("Bearer ") ? raw.split(" ")[1] : raw;

  try {
    const { exp } = JSON.parse(atob(token.split(".")[1]));
    const now = Date.now() / 1000;
    // si quedan menos de 120 s de vida
    if (exp - now < 120) {
      const res = await fetch(`${API_URL}/auth/refresh-token`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${raw}`
        }
      });
      if (res.ok) {
        const { token: newToken } = await res.json();
        localStorage.setItem("token", newToken);
        return newToken;
      }
    }
  } catch {
    // si algo falla, devolvemos el raw original
  }
  return raw;
}

export const customFetch = async (path, options = {}) => {
  const { skipRefresh = false, ...fetchOptions } = options;
  const maybe = !skipRefresh ? await maybeRefreshToken() : null;
  const token = maybe || localStorage.getItem("token");
  const url = path.startsWith("http") ? path : `${API_URL}${path}`;

  // Esta función siempre añade Authorization si hay token
  const createHeaders = (authToken) => {
    const isFormData = fetchOptions.body instanceof FormData;
    return {
      ...(!isFormData && { "Content-Type": "application/json" }),
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {})
    };
  };

  const fetchWithToken = (authToken) => {
    const opts = { ...fetchOptions, headers: createHeaders(authToken) };
    console.log(`📡 fetch → ${opts.method || "GET"} ${url}`);
    return fetch(url, opts);
  };

  try {
    let res = await fetchWithToken(token);

    // lógica de refresh para rutas propias (no MP)
    if (res.status === 401 && !skipRefresh) {
      console.warn("⚠️ 401 recibido, intentando refresh-token…");
      const newToken = await refreshToken();
      if (!newToken) {
        localStorage.removeItem("token");
        window.location.href = "/login";
        throw Object.assign(new Error("Sesión expirada"), { status: 401 });
      }
      console.info("🔁 Refresh exitoso, reintentando con nuevo token");
      res = await fetchWithToken(newToken);
      if (res.status === 401) {
        ["token","role","companyId","isAdmin","deviceId","branchId"].forEach(k => localStorage.removeItem(k));
        window.location.href = "/login";
        throw Object.assign(new Error("Requiere login de nuevo"), { status: 401 });
      }
    }

    const contentType = res.headers.get("Content-Type") || "";
    const body = contentType.includes("application/json") 
      ? await res.json() 
      : await res.text();

    if (!res.ok) {
      const err = new Error(body?.message || body?.error || res.statusText);
      err.status = res.status;
      err.data = body;
      throw err;
    }

    console.log(`✅ ${url} →`, body);
    return body;
  } catch (err) {
    console.error("❌ customFetch error completo:", err, "err.data=", err.data);
    throw err;
  }
};

/** refresca token (sólo usado para rutas propias, no MP) */
const refreshToken = async () => {
  console.log("🔄 Revisando refresh-token…");
  const token = localStorage.getItem("token");
  if (!token) {
    console.warn("🟡 Sin token para refrescar");
    return null;
  }
  try {
    const res = await fetch(`${API_URL}/auth/refresh-token`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      }
    });
    if (!res.ok) {
      console.warn(`🛑 refresh-token falló: ${res.status}`);
      return null;
    }
    const { token: newToken } = await res.json();
    if (!newToken) {
      console.warn("⚠️ refresh-token no devolvió nuevo token");
      return null;
    }
    localStorage.setItem("token", newToken);
    console.info("🟢 Guardado nuevo token");
    return newToken;
  } catch (err) {
    console.error("❌ Error en refresh-token:", err);
    return null;
  }
};
