import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { customFetch } from "../utils/api";

const TokenVerifier = () => {
  const navigate = useNavigate();

  useEffect(() => {
    const interval = setInterval(() => {
      const token = localStorage.getItem("token");
      if (!token) return;

      customFetch("/auth/me", { skipRefresh: true })
        .then(() => {
          console.log("✅ Token aún válido (verificación pasiva)");
        })
        .catch((err) => {
          if (err?.status === 401) {
            console.warn("⏱️ Token expirado detectado pasivamente. Cerrando sesión.");
            localStorage.removeItem("token");

            // 💬 Mensaje al usuario
            alert("Tu sesión ha expirado. Por favor, inicia sesión nuevamente.");

            navigate("/login");
          }
        });
    }, 15 * 60 * 1000); // Cada 15 minutos

    return () => clearInterval(interval);
  }, [navigate]);

  return null;
};

export default TokenVerifier;