import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AuthPage } from './pages/AuthPage';
import { EventsPage } from './pages/EventsPage';
import { OrganiserPage } from './pages/OrganiserPage';
const BookingPage = lazy(() => import('./pages/BookingPage'));
const MyBookingsPage = lazy(() => import('./pages/MyBookingsPage'));
function ScrollReset() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  return null;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <ScrollReset />
        <Suspense fallback={<main className="page" role="status">Loading your next experience…</main>}><Routes>
          <Route path="/events/:eventId/book" element={<ProtectedRoute><BookingPage /></ProtectedRoute>} />
          <Route path="/bookings" element={<ProtectedRoute><MyBookingsPage /></ProtectedRoute>} />
          <Route path="/auth" element={<AuthPage />} />
          <Route
            path="/events"
            element={
              <ProtectedRoute>
                <EventsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/organiser"
            element={
              <ProtectedRoute role="COMMITTEE_MEMBER">
                <OrganiserPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/events" replace />} />
        </Routes></Suspense>
      </BrowserRouter>
    </AuthProvider>
  );
}
