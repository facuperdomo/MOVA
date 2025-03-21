import React, { useState } from "react";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import "./App.css";
import LoginCompany from "./components/login/LoginCompany";
import LoginUser from "./components/login/LoginUser";
import Dashboard from "./components/dashboardComp/Dashboard";
import AdminOptions from "./components/admin/AdminOptions"; // Nueva pantalla para el admin
import PrivateRoute from "./components/PrivateRoute";
import AdminProducts from "./components/adminProducts/AdminProducts";
import { isTokenValid } from "./utils/authUtils";
import Statistics from "./components/statistics/Statistics";

const App = () => {
  return (
    <Router>
      <Routes>
        {/* Rutas públicas */}
        <Route path="/login" element={<LoginCompany />} />
        <Route path="/loginUser" element={<LoginUser />} />

        {/* Redireccionar si el token no es válido */}
        {!isTokenValid() ? (
          <Route path="*" element={<Navigate to="/login" replace />} />
        ) : (
          <>
            {/* Rutas protegidas solo para administradores */}
            <Route element={<PrivateRoute adminOnly={true} />}>
              <Route path="/admin-options" element={<AdminOptions />} />
              <Route path="/statistics" element={<Statistics />} />
            </Route>

            {/* Rutas protegidas para usuarios autenticados */}
            <Route element={<PrivateRoute />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/adminProducts" element={<AdminProducts />} />
            </Route>

            {/* Redireccionar cualquier otra ruta al dashboard si el usuario está autenticado */}
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </>
        )}
      </Routes>
    </Router>
  );
};

export default App;