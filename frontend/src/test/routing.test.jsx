import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '../App';

// 1. Mock AuthContext so ProtectedRoute doesn't redirect
vi.mock('../context/AuthContext', () => ({
  AuthProvider: ({ children }) => <div>{children}</div>,
  useAuth: () => ({ user: { id: 1, role: 'COMMITTEE_MEMBER' }, initialising: false }),
}));

// 2. Mock BrowserRouter so it doesn't conflict with MemoryRouter in the test
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    BrowserRouter: ({ children }) => <>{children}</>,
  };
});

// 3. Mock ALL the page components so they render simple text instead of making real API calls
vi.mock('../pages/BookingPage', () => ({ default: () => <div>BookingPage</div> }));
vi.mock('../pages/MyBookingsPage', () => ({ MyBookingsPage: () => <div>MyBookingsPage</div> }));
vi.mock('../pages/TicketAndSeatMapView', () => ({ TicketAndSeatMapView: () => <div>TicketView</div> }));
vi.mock('../pages/EventSeatingPage', () => ({ EventSeatingPage: () => <div>EventSeatingPage</div> }));
vi.mock('../pages/OrganiserDashboard', () => ({ OrganiserDashboard: () => <div>OrganiserDashboard</div> }));
vi.mock('../pages/EventCreationWizard', () => ({ EventCreationWizard: () => <div>EventCreationWizard</div> }));
vi.mock('../pages/EventsPage', () => ({ EventsPage: () => <div>EventsPage</div> }));
vi.mock('../pages/AuthPage', () => ({ AuthPage: () => <div>AuthPage</div> }));
vi.mock('../pages/OrganiserPage', () => ({ OrganiserPage: () => <div>OrganiserPage</div> }));

describe('App Routing Regression Tests', () => {
  const renderRoute = (route) => {
    return render(
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    );
  };

  it('routes /events/:eventId/book to BookingPage', () => {
    const { getByText } = renderRoute('/events/123/book');
    expect(getByText('BookingPage')).toBeInTheDocument();
  });

  it('routes /bookings to MyBookingsPage', () => {
    const { getByText } = renderRoute('/bookings');
    expect(getByText('MyBookingsPage')).toBeInTheDocument();
  });

  it('routes /bookings/:bookingId/ticket to TicketAndSeatMapView', () => {
    const { getByText } = renderRoute('/bookings/456/ticket');
    expect(getByText('TicketView')).toBeInTheDocument();
  });

  it('routes /organiser/events/:eventId to EventSeatingPage', () => {
    const { getByText } = renderRoute('/organiser/events/789');
    expect(getByText('EventSeatingPage')).toBeInTheDocument();
  });
});