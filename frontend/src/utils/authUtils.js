export const isTokenValid = () => {
    // 1) Recuperamos del localStorage
    const raw = localStorage.getItem("token");
    if (!raw) return false;
  
    // 2) Si viene con "Bearer ", lo separamos; si no, lo usamos tal cual
    const token = raw.startsWith("Bearer ")
      ? raw.split(" ")[1]
      : raw;
  
    try {
      // 3) Decodificamos payload y comprobamos expiración
      const payload = JSON.parse(atob(token.split(".")[1]));
      const isExpired = payload.exp * 1000 < Date.now();
  
      if (isExpired) {
        // 4) Si expiró, borramos todo y devolvemos false
        localStorage.removeItem("token");
        localStorage.removeItem("isAdmin");
        return false;
      }
  
      return true;
    } catch (error) {
      console.error("❌ Error al validar el token:", error);
      return false;
    }
  };