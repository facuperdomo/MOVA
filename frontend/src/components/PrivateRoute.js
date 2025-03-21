import { Navigate, Outlet } from "react-router-dom";
import { isTokenValid } from "../utils/authUtils";

const PrivateRoute = ({ adminOnly = false }) => {
    const tokenValid = isTokenValid();
    const isAdmin = localStorage.getItem("isAdmin") === "true";
    const role = localStorage.getItem("role");

    // Debugging para detectar el problema
    console.log("🔹 Token válido:", tokenValid);
    console.log("🔹 isAdmin:", isAdmin);
    console.log("🔹 Role:", role);

    // Si no hay token válido, redirige según el tipo de usuario
    if (!tokenValid) {
        return <Navigate to="/login" replace />;
    }

    // Si es una ruta de admin y el usuario no es admin, redirige según su rol
    if (adminOnly && !isAdmin) {
        return role === "COMPANY" ? <Navigate to="/admin-options" replace /> : <Navigate to="/dashboard" replace />;
    }

    return <Outlet />;
};

export default PrivateRoute;
