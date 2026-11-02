import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import BookingPage from '../pages/BookingPage';
import MyBookingsPage from '../pages/MyBookingsPage';
import { fetchEvent } from '../api/eventsApi';
import { mockCheckout, fetchMyBookings } from '../api/bookingsApi';

vi.mock('../api/eventsApi', () => ({ fetchEvent: vi.fn() }));
vi.mock('../api/bookingsApi', () => ({ mockCheckout: vi.fn(), fetchMyBookings: vi.fn() }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@uni.sydney.edu.au' }, logout: vi.fn() }) }));
const event = { id: 1, title: 'Campus gathering', dateTime: '2099-10-25T18:00:00', venue: 'Great Hall', category: 'Social', registrationStatus: 'OPEN', seatIdentifiersConfirmed: true };
const booking = { id: 1, eventId: 1, eventTitle: event.title, bookingRef: 'BKG-DEMO', status: 'PENDING_ALLOCATION', groupCode: 'GRP-DEMO' };
function renderBooking() {
  return render(<MemoryRouter initialEntries={['/events/1/book']}><Routes><Route path="/events/:eventId/book" element={<BookingPage />} /></Routes></MemoryRouter>);
}
async function review() { await userEvent.click(await screen.findByRole('button', { name: /Continue to mock checkout/ })); }
describe('attendee booking journey', () => {
  beforeEach(() => { vi.clearAllMocks(); fetchEvent.mockResolvedValue(event); });
  it('creates a solo booking only after explicit mock success', async () => {
    mockCheckout.mockResolvedValue({ outcome: 'SUCCESS', booking }); renderBooking(); await review();
    expect(mockCheckout).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /Simulate successful/ }));
    expect(await screen.findByText('Booking confirmed.')).toBeInTheDocument();
    expect(mockCheckout).toHaveBeenCalledWith('1', expect.objectContaining({ createGroup: false, joinGroupCode: null, outcome: 'SUCCESS' }));
  });
  it('sends the private-group and accessibility choices', async () => {
    mockCheckout.mockResolvedValue({ outcome: 'SUCCESS', booking }); renderBooking();
    await userEvent.click(await screen.findByRole('radio', { name: /Start a group/ }));
    await userEvent.click(screen.getByRole('checkbox')); await review();
    await userEvent.click(screen.getByRole('button', { name: /Simulate successful/ }));
    expect(await screen.findByText('GRP-DEMO')).toBeInTheDocument();
    expect(mockCheckout).toHaveBeenCalledWith('1', expect.objectContaining({ createGroup: true, accessibilityRequired: true }));
  });
  it('normalises a join code without creating a new group', async () => {
    mockCheckout.mockResolvedValue({ outcome: 'SUCCESS', booking }); renderBooking();
    await userEvent.click(await screen.findByRole('radio', { name: /Join my friends/ }));
    await userEvent.type(screen.getByLabelText('Group code'), 'grp-demo'); await review();
    await userEvent.click(screen.getByRole('button', { name: /Simulate successful/ }));
    expect(mockCheckout).toHaveBeenCalledWith('1', expect.objectContaining({ joinGroupCode: 'GRP-DEMO', createGroup: false }));
  });
  it.each(['DECLINED', 'CANCELLED'])('explains that %s creates no booking', async outcome => {
    mockCheckout.mockResolvedValue({ outcome, booking: null }); renderBooking(); await review();
    await userEvent.click(screen.getByRole('button', { name: outcome === 'DECLINED' ? 'Simulate decline' : 'Cancel checkout' }));
    expect(await screen.findByText(/No booking was created/)).toBeInTheDocument();
  });
  it('reuses exactly the same request after an uncertain network failure', async () => {
    mockCheckout.mockRejectedValueOnce(Object.assign(new Error('Offline'), { status: 0 })).mockResolvedValueOnce({ outcome: 'SUCCESS', booking });
    renderBooking(); await review(); await userEvent.click(screen.getByRole('button', { name: /Simulate successful/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Retry same checkout' }));
    expect(await screen.findByText('Booking confirmed.')).toBeInTheDocument();
    expect(mockCheckout.mock.calls[0][1]).toEqual(mockCheckout.mock.calls[1][1]);
  });
  it('does not offer checkout on a closed event', async () => {
    fetchEvent.mockResolvedValue({ ...event, registrationStatus: 'CLOSED' }); renderBooking();
    expect(await screen.findByText('Registration is unavailable.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Continue to/ })).not.toBeInTheDocument();
  });
  it('offers individual booking only when seat identifiers are unconfirmed', async () => {
    fetchEvent.mockResolvedValue({ ...event, seatIdentifiersConfirmed: false }); renderBooking();
    await screen.findByRole('radio', { name: /Just me/ });
    expect(screen.queryByRole('radio', { name: /Start a group/ })).not.toBeInTheDocument();
  });
  it('explains general admission after successful checkout', async () => {
    fetchEvent.mockResolvedValue({ ...event, seatIdentifiersConfirmed: false });
    mockCheckout.mockResolvedValue({ outcome: 'SUCCESS', booking: { ...booking, generalAdmission: true } });
    renderBooking(); await review();
    await userEvent.click(screen.getByRole('button', { name: /Simulate successful/ }));
    expect(await screen.findByText(/General admission: no physical seat will be assigned/)).toBeInTheDocument();
    expect(screen.queryByText(/Seat allocation is pending/)).not.toBeInTheDocument();
  });
  it('does not label a general-admission booking as awaiting a seat', async () => {
    fetchMyBookings.mockResolvedValue([{ ...booking, generalAdmission: true }]);
    render(<MemoryRouter><MyBookingsPage /></MemoryRouter>);
    expect(await screen.findByText('General admission')).toBeInTheDocument();
    expect(screen.queryByText('Awaiting seat allocation')).not.toBeInTheDocument();
  });
  it('shows pending allocation honestly in My bookings', async () => {
    fetchMyBookings.mockResolvedValue([booking]); render(<MemoryRouter><MyBookingsPage /></MemoryRouter>);
    expect(await screen.findByText('Awaiting seat allocation')).toBeInTheDocument();
    expect(screen.getByText(/no seat has been assigned yet/)).toBeInTheDocument();
  });
});
