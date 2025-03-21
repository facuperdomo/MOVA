export const isTokenValid = () => {
    const token = localStorage.getItem("token");
    if (!token) return false;

    try {
        const payload = JSON.parse(atob(token.split(".")[1])); // Decodifica el payload del JWT
        const isExpired = payload.exp * 1000 < Date.now();

        if (isExpired) {
            // ✅ Borra el token y la info de admin si el token está vencido
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