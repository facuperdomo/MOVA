import { Navigate, Outlet } from 'react-router-dom';
import { isTokenValid } from '../utils/authUtils';

const PrivateRoute = ({ adminOnly = false, kitchenOnly = false }) => {
  const tokenValid = isTokenValid();
  const role       = localStorage.getItem('role');

  if (!tokenValid) {
    // no logueado → vuelve a la home común
    return <Navigate to="/" replace />;
  }

  if (adminOnly && role !== 'ADMIN') {
    // quien no es ADMIN no puede entrar al panel de admins
    return <Navigate to={ role === 'KITCHEN' ? '/kitchen-dashboard' : '/dashboard' } replace />;
  }

  if (kitchenOnly && role !== 'KITCHEN') {
    // quien no es cocina no puede entrar al tablero de cocina
    return <Navigate to={ role === 'ADMIN' ? '/admin-options' : '/dashboard' } replace />;
  }

  // usuario normal / admin / cocina retienen acceso
  return <Outlet />;
};

export default PrivateRoute;