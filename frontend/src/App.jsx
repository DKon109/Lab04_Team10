import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AuthPage } from './pages/AuthPage';
import { EventsPage } from './pages/EventsPage';
import { OrganiserPage } from './pages/OrganiserPage';
import { OrganiserDashboard } from './pages/OrganiserDashboard';
import { EventCreationWizard } from './pages/EventCreationWizard';
import { TicketAndSeatMapView } from './pages/TicketAndSeatMapView';

import BookingPage from './pages/BookingPage';
import MyBookingsPage from './pages/MyBookingsPage';
import EventSeatingPage from './pages/EventSeatingPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/events" element={<ProtectedRoute><EventsPage /></ProtectedRoute>} />
          
          {/* RESTORED ATTENDEE ROUTES */}
          <Route path="/events/:eventId/book" element={<ProtectedRoute><BookingPage /></ProtectedRoute>} />
          <Route path="/bookings" element={<ProtectedRoute><MyBookingsPage /></ProtectedRoute>} />
          <Route path="/bookings/:bookingId/ticket" element={<ProtectedRoute><TicketAndSeatMapView /></ProtectedRoute>} />
          
          {/* ORGANISER ROUTES */}
          <Route path="/organiser" element={<Navigate to="/organiser/dashboard" replace />} />
          <Route path="/organiser/dashboard" element={<ProtectedRoute role="COMMITTEE_MEMBER"><OrganiserDashboard /></ProtectedRoute>} />
          <Route path="/organiser/new" element={<ProtectedRoute role="COMMITTEE_MEMBER"><EventCreationWizard /></ProtectedRoute>} />
          
          {/* ORGANISER MANAGEMENT ROUTES */}
          <Route path="/organiser/events/:eventId" element={<ProtectedRoute role="COMMITTEE_MEMBER"><EventSeatingPage /></ProtectedRoute>} />
          
          {/* FIXED: Edit route now goes to OrganiserPage */}
          <Route path="/organiser/events/:eventId/edit" element={<ProtectedRoute role="COMMITTEE_MEMBER"><OrganiserPage /></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/events" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
