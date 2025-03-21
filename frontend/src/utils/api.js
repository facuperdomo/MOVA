export const customFetch = async (url, options = {}) => {
    let token = localStorage.getItem("token");
  
    const headers = {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };
  
    try {
      let response = await fetch(url, { ...options, headers });
  
      // 🔹 Si el token ha expirado, intentar refrescarlo
      if (response.status === 401) {
        console.warn("⚠️ Token expirado. Intentando refrescar...");
  
        const newToken = await refreshToken();
        if (!newToken) {
          console.error("❌ No se pudo refrescar el token. Redirigiendo al login...");
          localStorage.removeItem("token"); // Elimina token inválido
          localStorage.removeItem("isAdmin"); // Elimina flag de admin
          window.location.href = "/login"; // Redirige al login
          return Promise.reject(new Error("Sesión expirada. Redirigiendo al login."));
        }
  
        // 🔹 Reintentar la solicitud con el nuevo token
        headers.Authorization = `Bearer ${newToken}`;
        response = await fetch(url, { ...options, headers });
      }
  
      // ✅ Determinar el tipo de respuesta y leerlo solo una vez
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
   * ✅ Función para refrescar el token llamando al backend.
   */
  const refreshToken = async () => {
    const token = localStorage.getItem("token");
    if (!token) return null;
  
    try {
      const response = await fetch("http://localhost:8080/auth/refresh-token", {
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
      localStorage.setItem("token", newToken); // 🔹 Guardar nuevo token
      console.log("✅ Token refrescado correctamente.");
      return newToken;
    } catch (error) {
      console.error("❌ Error en la solicitud de refresh token:", error);
      return null;
    }
  };
  