// src/App.js
import React from "react";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import "./App.css";

import LoginCompany from "./components/login/LoginCompany";
import LoginUser from "./components/login/LoginUser";
import LoginSuperAdmin from "./components/login/LoginSuperAdmin";
import Dashboard from "./components/dashboardComp/Dashboard";
import AdminOptions from "./components/admin/AdminOptions";
import AdminProducts from "./components/adminProducts/AdminProducts";
import Statistics from "./components/statistics/Statistics";
import KitchenDashboard from "./components/kitchen/KitchenDashboard";
import SuperAdminDashboard from './components/superadmin/SuperAdminDashboard';
import CompanyStatistics from './components/statistics/CompanyStatistics';
import BranchStatistics from './components/statistics/BranchStatistics';

import PrivateRoute from "./components/PrivateRoute";
import PublicRoute from "./components/PublicRoute";
import { isTokenValid } from "./utils/authUtils";
import TokenVerifier from "./components/TokenVerifier";

function App() {
  const role = localStorage.getItem("role"); // 'SUPERADMIN', 'ADMIN', 'USER', etc.

  return (
    <Router>
      <TokenVerifier />
      <Routes>
        {/* Home redirige según sesión y rol */}
        <Route
          path="/"
          element={
            isTokenValid()
              ? role === "SUPERADMIN"
                ? <Navigate to="/superadmin-dashboard" replace />
                : <Navigate to="/dashboard" replace />
              : <Navigate to="/login" replace />
          }
        />

        {/* Rutas de login públicas */}
        <Route element={<PublicRoute />}>
          <Route path="/login" element={<LoginCompany />} />
          <Route path="/loginUser" element={<LoginUser />} />
          <Route path="/superadmin-login" element={<LoginSuperAdmin />} />
        </Route>

        {/* SUPERADMIN */}
        <Route element={<PrivateRoute superadminOnly />}>
          <Route path="/superadmin-dashboard" element={<SuperAdminDashboard />} />
          <Route path="/company-statistics/:companyId" element={<CompanyStatistics />} />
          <Route path="/branch-statistics/:branchId" element={<BranchStatistics />} />
        </Route>

        {/* ADMIN */}
        <Route element={<PrivateRoute adminOnly />}>
          <Route path="/admin-options" element={<AdminOptions />} />
          <Route path="/statistics" element={<Statistics />} />
          <Route path="/adminProducts" element={<AdminProducts />} />
        </Route>

        {/* USER */}
        <Route element={<PrivateRoute userOnly />}>
          <Route path="/dashboard" element={<Dashboard />} />
        </Route>

        {/* COCINA */}
        <Route element={<PrivateRoute kitchenOnly />}>
          <Route path="/kitchen-dashboard" element={<KitchenDashboard />} />
        </Route>

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}

export default App;
