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
    expect(api.allocateSeats).toHaveBeenCalledWith('1', 'DEFAULT');
  });
  it('sends the selected allocation preference and preserves it after a failed attempt', async () => {
    api.fetchSeating.mockResolvedValue({ ...management, event: { ...management.event, status: 'closed' } });
    api.allocateSeats.mockRejectedValueOnce(new Error('Allocation failed. Try again.'));
    renderManagement();
    await userEvent.selectOptions(await screen.findByLabelText('Allocation preference'), 'PREFER_SAME_ROW');
    await userEvent.click(screen.getByRole('button', { name: 'Run allocation' }));
    expect(api.allocateSeats).toHaveBeenCalledWith('1', 'PREFER_SAME_ROW');
    expect(await screen.findByRole('alert')).toHaveTextContent('Allocation failed. Try again.');
    expect(screen.getByLabelText('Allocation preference')).toHaveValue('PREFER_SAME_ROW');
  });
  it('shows the persisted preference and locks it after allocation', async () => {
    api.fetchSeating.mockResolvedValue({ ...management, event: { ...management.event, status: 'closed' },
      allocation: { strategy: 'PREFER_SAME_ROW', bookingCount: 2, splitGroupCount: 0, elapsedMillis: 10 } });
    renderManagement();
    expect(await screen.findByText('Allocation preference: Prefer same row')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Run allocation' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Allocation preference')).not.toBeInTheDocument();
  });
  it('does not offer seating preferences for general admission', async () => {
    api.fetchSeating.mockResolvedValue({ ...management, event: { ...management.event, status: 'closed', seatIdentifiersConfirmed: false } });
    renderManagement();
    await userEvent.click(await screen.findByRole('button', { name: 'Run allocation' }));
    expect(screen.queryByLabelText('Allocation preference')).not.toBeInTheDocument();
    expect(api.allocateSeats).toHaveBeenCalledWith('1', 'DEFAULT');
  });
  it('shows the real assigned seat and split explanation, without mock attendee names', async () => {
    api.fetchTicket.mockResolvedValue({ booking: { eventTitle: 'Film night', bookingRef: 'BKG-123', status: 'ALLOCATED' }, paymentStatus: 'MOCK_SUCCESS', seatCode: 'B2', splitReason: 'No contiguous valid block remained.', generalAdmission: false, eventDate: '2099-01-01T18:00:00', venue: 'Demo hall', layout: null });
    renderTicket(); expect(await screen.findByText('B2')).toBeInTheDocument();
    expect(screen.getByText('Your group is split.')).toBeInTheDocument();
    expect(screen.queryByText('Alex')).not.toBeInTheDocument();
    expect(api.fetchTicket).toHaveBeenCalledWith('5');
  });
  it('renders a managed map and identifies the attendee seat', async () => {
    api.fetchTicket.mockResolvedValue({ booking: { eventTitle: 'Film night', bookingRef: 'BKG-123', status: 'ALLOCATED' },
      paymentStatus: 'MOCK_SUCCESS', seatCode: 'A2', generalAdmission: false, eventDate: '2099-01-01T18:00:00', venue: 'Demo hall', layout: null,
      managedMap: { name: 'Main auditorium', version: 2, elements: [
        { key: 'row-a', label: 'Row A', positionX: 0, positionY: 0, rotationDegrees: 0, seats: [
          { code: 'A1', label: 'A1', positionX: 10, positionY: 20, accessible: true, status: 'AVAILABLE' },
          { code: 'A2', label: 'A2', positionX: 20, positionY: 20, accessible: false, status: 'ASSIGNED' }
        ] },
        { key: 'stage', label: 'Stage', positionX: 0, positionY: -20, rotationDegrees: 0, seats: [] }
      ] } });
    renderTicket();
    expect(await screen.findByRole('img', { name: 'Seat map: Main auditorium. Your seat is A2.' })).toBeInTheDocument();
    expect(screen.getByText('Stage')).toBeInTheDocument();
    expect(screen.getByText('A1 · Accessible · AVAILABLE')).toBeInTheDocument();
    expect(screen.getByText('A2 · Your seat · ASSIGNED')).toBeInTheDocument();
    expect(screen.queryByText('The organiser has not saved a seat map yet.')).not.toBeInTheDocument();
  });
  it('continues to render legacy ticket layouts', async () => {
    api.fetchTicket.mockResolvedValue({ booking: { eventTitle: 'Film night', bookingRef: 'BKG-123', status: 'ALLOCATED' },
      paymentStatus: 'MOCK_SUCCESS', seatCode: 'A1', generalAdmission: false, eventDate: '2099-01-01T18:00:00', venue: 'Demo hall',
      layout: { template: 'FRONT', rows: 1, seatsPerRow: 1, aisleAfter: 0, version: 1,
        seats: [{ code: 'A1', row: 0, column: 1, accessible: false, blocked: false }] } });
    renderTicket();
    expect(await screen.findByRole('img', { name: /Seat map. Your seat is A1/ })).toBeInTheDocument();
  });
  it('does not invent an assigned seat while allocation is pending', async () => {
    api.fetchTicket.mockResolvedValue({ booking: { eventTitle: 'Film night', bookingRef: 'BKG-123', status: 'PENDING_ALLOCATION' }, paymentStatus: 'MOCK_SUCCESS', seatCode: null, generalAdmission: false, eventDate: '2099-01-01T18:00:00', venue: 'Demo hall', layout: null });
    renderTicket(); expect(await screen.findByText(/No seat has been assigned yet/)).toBeInTheDocument();
    expect(screen.queryByText('YOUR ASSIGNED SEAT')).not.toBeInTheDocument();
  });
});
