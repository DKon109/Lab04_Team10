import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import EventSeatingPage from '../pages/EventSeatingPage';
import { TicketAndSeatMapView } from '../pages/TicketAndSeatMapView';
import * as api from '../api/seatingApi';
import { setEventStatus } from '../api/organiserApi';
vi.mock('../api/seatingApi', () => ({ fetchSeating: vi.fn(), saveSeating: vi.fn(), allocateSeats: vi.fn(), fetchSeatingProfiles: vi.fn(), fetchAllocationExport: vi.fn(), fetchTicket: vi.fn() }));
vi.mock('../api/organiserApi', () => ({ setEventStatus: vi.fn() }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@uni.sydney.edu.au' }, logout: vi.fn() }) }));
const management = { event: { id: 1, title: 'Film night', status: 'draft', capacity: 20, seatIdentifiersConfirmed: true }, confirmedBookings: 2, unverifiedLegacyBookings: 0, allocation: null, layout: null };
function renderManagement() { render(<MemoryRouter initialEntries={['/organiser/events/1']}><Routes><Route path="/organiser/events/:eventId" element={<EventSeatingPage />} /></Routes></MemoryRouter>); }
function renderTicket() { render(<MemoryRouter initialEntries={['/bookings/5/ticket']}><Routes><Route path="/bookings/:bookingId/ticket" element={<TicketAndSeatMapView />} /></Routes></MemoryRouter>); }
describe('persisted seating experience', () => {
  beforeEach(() => { vi.clearAllMocks(); api.fetchSeating.mockResolvedValue(management); api.fetchSeatingProfiles.mockResolvedValue([]); });
  it('saves a validated template payload with accessible and blocked seat codes', async () => {
    renderManagement(); await screen.findByText('Set up your venue');
    await userEvent.type(screen.getByLabelText('Blocked seats (comma separated)'), 'B3, B4');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Save venue configuration' }));
    expect(api.saveSeating).toHaveBeenCalledWith('1', expect.objectContaining({ source: 'EXTERNAL', template: 'FRONT', blockedCodes: ['B3', 'B4'], accessibleCodes: ['A1', 'A2'], identifiersConfirmed: true }));
  });
  it('requires explicit confirmation before closing registrations', async () => {
    api.fetchSeating.mockResolvedValue({ ...management, event: { ...management.event, status: 'published' } });
    renderManagement(); await userEvent.click(await screen.findByRole('button', { name: 'Close registration…' }));
    expect(setEventStatus).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm close registration' }));
    expect(setEventStatus).toHaveBeenCalledWith('1', 'closed');
  });
  it('only exposes allocation for a closed event with confirmed bookings', async () => {
    api.fetchSeating.mockResolvedValue({ ...management, event: { ...management.event, status: 'closed' } });
    renderManagement(); await userEvent.click(await screen.findByRole('button', { name: 'Run allocation' }));
    expect(api.allocateSeats).toHaveBeenCalledWith('1');
  });
  it('shows the real assigned seat and split explanation, without mock attendee names', async () => {
    api.fetchTicket.mockResolvedValue({ booking: { eventTitle: 'Film night', bookingRef: 'BKG-123', status: 'ALLOCATED' }, paymentStatus: 'MOCK_SUCCESS', seatCode: 'B2', splitReason: 'No contiguous valid block remained.', generalAdmission: false, eventDate: '2099-01-01T18:00:00', venue: 'Demo hall', layout: null });
    renderTicket(); expect(await screen.findByText('B2')).toBeInTheDocument();
    expect(screen.getByText('Your group is split.')).toBeInTheDocument();
    expect(screen.queryByText('Alex')).not.toBeInTheDocument();
    expect(api.fetchTicket).toHaveBeenCalledWith('5');
  });
  it('does not invent an assigned seat while allocation is pending', async () => {
    api.fetchTicket.mockResolvedValue({ booking: { eventTitle: 'Film night', bookingRef: 'BKG-123', status: 'PENDING_ALLOCATION' }, paymentStatus: 'MOCK_SUCCESS', seatCode: null, generalAdmission: false, eventDate: '2099-01-01T18:00:00', venue: 'Demo hall', layout: null });
    renderTicket(); expect(await screen.findByText(/No seat has been assigned yet/)).toBeInTheDocument();
    expect(screen.queryByText('YOUR ASSIGNED SEAT')).not.toBeInTheDocument();
  });
});
