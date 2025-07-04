// src/components/PublicRoute.jsx
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { isTokenValid } from "../utils/authUtils";

export default function PublicRoute() {
  const location = useLocation();
  const raw = localStorage.getItem("token");
  if (!raw) {
    // no hay token → rutas de login accesibles
    return <Outlet />;
  }

  // intentamos extraer el authType del payload
  let authType = null;
  try {
    const payload = JSON.parse(atob(raw.split(".")[1]));
    authType = payload.authType;
  } catch {
    // si falla el parse, borramos token para limpiar estado
    localStorage.removeItem("token");
    return <Outlet />;
  }

  // 1) Si es un branch‐token (recien loginCompany), dejamos pasar
  if (authType === "BRANCH") {
    return <Outlet />;
  }

  // 2) Si es un token de usuario válido, redirigimos a dashboard
  if (authType === "USER" && isTokenValid()) {
    return <Navigate to="/dashboard" state={{ from: location }} replace />;
  }

  // 3) En cualquier otro caso (p.ej. expiró o no coincide), limpiamos y dejamos entrar
  localStorage.removeItem("token");
  return <Outlet />;
}
