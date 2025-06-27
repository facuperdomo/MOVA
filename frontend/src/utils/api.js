import { API_URL } from "../config/apiConfig";

export const customFetch = async (path, options = {}) => {
  const { skipRefresh = false, ...fetchOptions } = options;
  const token = localStorage.getItem("token");
  const url = path.startsWith("http") ? path : `${API_URL}${path}`;
  const isMercadoPago = url.includes("/api/mercadopago/");

  const createHeaders = (authToken) => {
    const isFormData = fetchOptions.body instanceof FormData;
    return {
      ...(!isFormData && { "Content-Type": "application/json" }),
      ...(!isMercadoPago && authToken ? { Authorization: `Bearer ${authToken}` } : {})
    };
  };

  const fetchWithToken = async (authToken) => {
    const headers = createHeaders(authToken);
    const opts = { ...fetchOptions, headers };
    console.log(`📡 fetch → ${opts.method || "GET"} ${url}`, { headers, skipRefresh });
    return fetch(url, opts);
  };

  try {
    let res = await fetchWithToken(token);

    if (!isMercadoPago && res.status === 401 && !skipRefresh) {
      console.warn("⚠️ 401 recibido, intentando refresh-token…");
      const newToken = await refreshToken();
      if (!newToken) {
        console.error("🛑 No pude refrescar el token → redirigiendo a login");
        localStorage.removeItem("token");
        window.location.href = "/login";
        throw Object.assign(new Error("Sesión expirada"), { status: 401 });
      }
      console.info("🔁 Refresh exitoso, retry fetch con nuevo token");
      res = await fetchWithToken(newToken);
      if (res.status === 401) {
        console.error("💥 Sigue 401 tras refresh → tokenVersion desincronizado");
        localStorage.removeItem("token");
        window.location.href = "/login";
        throw Object.assign(new Error("Requiere login de nuevo"), { status: 401 });
      }
    }

    const contentType = res.headers.get("Content-Type") || "";
    const body = contentType.includes("application/json")
      ? await res.json()
      : await res.text();

    if (!res.ok) {
      console.warn(`🚫 HTTP ${res.status} en ${url}`, body);
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
