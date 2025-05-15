// src/components/PrivateRoute.js
import { Navigate, Outlet } from 'react-router-dom';
import { isTokenValid } from '../utils/authUtils';

const PrivateRoute = ({ adminOnly = false, kitchenOnly = false }) => {
  const tokenValid = isTokenValid();
  const role       = localStorage.getItem('role');

  if (!tokenValid) {
    return <Navigate to="/login" replace />;
  }

  if (adminOnly && role !== 'ADMIN') {
    return <Navigate to={role === 'KITCHEN' ? '/kitchen-dashboard' : '/dashboard'} replace />;
  }

  if (kitchenOnly && role !== 'KITCHEN') {
    return <Navigate to={role === 'ADMIN' ? '/admin-options' : '/dashboard'} replace />;
  }

  return <Outlet />;
};

export default PrivateRoute;
