import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

/** Redirects unauthenticated visitors to /auth; optionally requires a role. */
export function ProtectedRoute({ children, role }) {
  const { user, initialising } = useAuth();

  if (initialising) return null;
  if (!user) return <Navigate to="/auth" replace />;
  if (role && user.role !== role) return <Navigate to="/events" replace />;
  return children;
}
