import { API_URL } from "../config/apiConfig";

export const customFetch = async (path, options = {}) => {
  const { skipRefresh = false, ...fetchOptions } = options;
  const token = localStorage.getItem("token");
  const url = path.startsWith("http") ? path : `${API_URL}${path}`;
  const isMercadoPago = url.includes("/api/mercadopago/");

  const createHeaders = (authToken) => ({
    "Content-Type": "application/json",
    ...(!isMercadoPago && authToken ? { Authorization: `Bearer ${authToken}` } : {})
  });

  const fetchWithToken = async (authToken) => {
    const headers = createHeaders(authToken);
    const opts = { ...fetchOptions, headers };

    console.log(`📡 fetch → ${opts.method || "GET"} ${url}`, {
      token: authToken?.substring(0, 10) + "...",
      skipRefresh
    });

    return await fetch(url, opts);
  };

  try {
    let res = await fetchWithToken(token);

    // ⚠️ Primer intento: 401 y no es MercadoPago → intentar refresh
    if (!isMercadoPago && res.status === 401 && !skipRefresh) {
      console.warn("⚠️ Token expirado. Intentando refresh…");

      const newToken = await refreshToken();
      if (!newToken) {
        console.error("🛑 No se pudo refrescar el token. Redirigiendo al login.");
        localStorage.removeItem("token");
        window.location.href = "/login";
        throw Object.assign(new Error("Sesión expirada"), { status: 401 });
      }

      console.info("🔁 Nuevo token obtenido, reintentando request…");
      res = await fetchWithToken(newToken);

      // 🧨 Si sigue dando 401 → probable tokenVersion inválido
      if (res.status === 401) {
        console.error("💥 Token aún inválido tras refresh: posible tokenVersion desincronizado");
        localStorage.removeItem("token");
        window.location.href = "/login";
        throw Object.assign(new Error("Sesión inválida (tokenVersion). Requiere login."), { status: 401 });
      }
    }

    const contentType = res.headers.get("Content-Type") || "";
    const isJson = contentType.includes("application/json");
    const body = isJson ? await res.json() : await res.text();

    if (!res.ok) {
      const err = new Error(body?.message || res.statusText);
      err.status = res.status;
      err.data = body;
      console.warn(`🚫 Respuesta HTTP ${res.status} al acceder a ${url}`, body);
      throw err;
    }

    console.log(`✅ Respuesta exitosa de ${url}`, body);
    return body;

  } catch (err) {
    console.error("❌ customFetch error:", err);
    throw err;
  }
};


/** Intenta refrescar el token y retorna el nuevo o null */
const refreshToken = async () => {
  console.warn("⚠️ Token expirado. Intentando refresh…");
  const token = localStorage.getItem("token");
  if (!token) {
    console.warn("🟡 No hay token en localStorage para refrescar.");
    return null;
  }

  try {
    console.log("🔄 Intentando refresh token...");

    const res = await fetch(`${API_URL}/auth/refresh-token`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      }
    });

    if (!res.ok) {
      console.warn(`🛑 Refresh-token falló con status ${res.status}`);
      return null;
    }

    const { token: newToken } = await res.json();
    if (!newToken) {
      console.warn("⚠️ La respuesta del refresh no trajo newToken");
      return null;
    }

    localStorage.setItem("token", newToken);
    console.info("🟢 Nuevo token guardado en localStorage");
    return newToken;

  } catch (err) {
    console.error("❌ Error al hacer refresh token:", err);
    return null;
  }
};
