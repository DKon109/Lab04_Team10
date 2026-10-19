import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AuthPage } from './pages/AuthPage';
import { EventsPage } from './pages/EventsPage';
import { OrganiserPage } from './pages/OrganiserPage';
import { OrganiserDashboard } from './pages/OrganiserDashboard';
const EventSeatingPage = lazy(() => import('./pages/EventSeatingPage'));
const TicketPage = lazy(() => import('./pages/TicketAndSeatMapView').then(module => ({ default: module.TicketAndSeatMapView })));
const BookingPage = lazy(() => import('./pages/BookingPage'));
const MyBookingsPage = lazy(() => import('./pages/MyBookingsPage'));
function ScrollReset() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  return null;
}

// --- MEMBER 2 IMPORTS ---
import { OrganiserDashboard } from './pages/OrganiserDashboard';
import { EventCreationWizard } from './pages/EventCreationWizard';
import { TicketAndSeatMapView } from './pages/TicketAndSeatMapView';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <ScrollReset />
        <Suspense fallback={<main className="page" role="status">Loading your next experience…</main>}><Routes>
          <Route path="/bookings/:bookingId/ticket" element={<ProtectedRoute><TicketPage /></ProtectedRoute>} />
          <Route path="/organiser/new" element={<ProtectedRoute role="COMMITTEE_MEMBER"><OrganiserPage /></ProtectedRoute>} />
          <Route path="/organiser/events/:eventId/edit" element={<ProtectedRoute role="COMMITTEE_MEMBER"><OrganiserPage /></ProtectedRoute>} />
          <Route path="/organiser/events/:eventId" element={<ProtectedRoute role="COMMITTEE_MEMBER"><EventSeatingPage /></ProtectedRoute>} />
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
          
          {/* Member 1's original page */}
          <Route
            path="/organiser"
            element={
              <ProtectedRoute role="COMMITTEE_MEMBER">
                <OrganiserDashboard />
              </ProtectedRoute>
            }
          />

          {/* --- MEMBER 2 ROUTES (SECURED) --- */}
          <Route
            path="/organiser/dashboard"
            element={
              <ProtectedRoute role="COMMITTEE_MEMBER">
                <OrganiserDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/organiser/new"
            element={
              <ProtectedRoute role="COMMITTEE_MEMBER">
                <EventCreationWizard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/ticket/:bookingId"
            element={
              <ProtectedRoute>
                <TicketAndSeatMapView />
              </ProtectedRoute>
            }
          />

          <Route path="*" element={<Navigate to="/events" replace />} />
        </Routes></Suspense>
      </BrowserRouter>
    </AuthProvider>
  );
}