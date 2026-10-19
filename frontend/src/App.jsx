import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AuthPage } from './pages/AuthPage';
import { EventsPage } from './pages/EventsPage';
import { OrganiserPage } from './pages/OrganiserPage';
import { OrganiserDashboard } from './pages/OrganiserDashboard';
import { EventCreationWizard } from './pages/EventCreationWizard';
import { TicketAndSeatMapView } from './pages/TicketAndSeatMapView';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/events" element={<ProtectedRoute><EventsPage /></ProtectedRoute>} />
          <Route path="/organiser" element={<ProtectedRoute role="COMMITTEE_MEMBER"><OrganiserPage /></ProtectedRoute>} />
          <Route path="/organiser/dashboard" element={<ProtectedRoute role="COMMITTEE_MEMBER"><OrganiserDashboard /></ProtectedRoute>} />
          
          {/* Consolidated single route */}
          <Route path="/organiser/new" element={<ProtectedRoute role="COMMITTEE_MEMBER"><EventCreationWizard /></ProtectedRoute>} />
          
          <Route path="/ticket/:bookingId" element={<ProtectedRoute><TicketAndSeatMapView /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/events" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}