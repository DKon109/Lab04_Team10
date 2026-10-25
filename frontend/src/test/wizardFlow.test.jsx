import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { EventCreationWizard } from '../pages/EventCreationWizard';
import * as eventsApi from '../api/eventsApi';
import * as apiClient from '../api/apiClient';

vi.mock('../context/AuthContext', () => ({
  AuthProvider: ({ children }) => <div>{children}</div>,
  useAuth: () => ({ user: { id: 1, role: 'COMMITTEE_MEMBER' }, logout: vi.fn() }),
}));

vi.mock('../api/eventsApi', () => ({ createEvent: vi.fn() }));
vi.mock('../api/apiClient', () => ({ request: vi.fn() }));

vi.mock('../components/layout/TopNav', () => ({
  TopNav: () => <nav data-testid="topnav" />,
}));

// Mock VenuePicker so we can actually reach Step 5 without async UI warnings
vi.mock('../components/VenuePicker', () => ({
  VenuePicker: ({ onSelect }) => (
    <button
      type="button"
      data-testid="pick-venue"
      onClick={() =>
        onSelect({
          mode: 'usyd',
          roomId: 1,
          rows: 10,
          seatsPerRow: 20,
          hasNumberedSeats: true,
          capacity: 200,
        })
      }
    >
      Pick venue
    </button>
  ),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

async function fillWizard() {
  // Step 1
  fireEvent.change(screen.getByLabelText(/Title/i), { target: { value: 'Test Event' } });
  fireEvent.change(screen.getByLabelText(/Category/i), { target: { value: 'Tech' } });
  fireEvent.click(screen.getByText('Next'));

  // Step 2
  fireEvent.change(screen.getByLabelText(/Date and time/i), { target: { value: '2026-12-01T10:00' } });
  fireEvent.change(screen.getByLabelText(/Venue name/i), { target: { value: 'Test Venue' } });
  fireEvent.click(screen.getByText('Next'));

  // Step 3 (Uses our mocked VenuePicker)
  fireEvent.click(screen.getByTestId('pick-venue'));
  fireEvent.click(screen.getByText('Next'));

  // Step 4
  fireEvent.change(screen.getByLabelText(/Organiser name/i), { target: { value: 'Test Society' } });
  fireEvent.click(screen.getByText('Next'));

  // Now on Step 5 (Ready to Publish)
}

describe('Wizard Publish Flow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
  });

  it('does not create a duplicate draft when seating save fails then retries', async () => {
    eventsApi.createEvent.mockResolvedValue({ id: 'draft-123' });

    // Attempt 1: Seating fails. 
    // Attempt 2: PUT update succeeds, seating save succeeds, publish succeeds.
    apiClient.request
      .mockRejectedValueOnce(new Error('seating save failed')) 
      .mockResolvedValueOnce({ ok: true })  // Attempt 2: PUT /events/draft-123
      .mockResolvedValueOnce({ ok: true })  // Attempt 2: Seating save
      .mockResolvedValueOnce({ ok: true }); // Attempt 2: Publish

    render(<MemoryRouter><EventCreationWizard /></MemoryRouter>);
    await fillWizard();

    // First click: Should fail and show banner
    fireEvent.click(screen.getByText(/Publish event/i));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    
    // Assert draft was only created once, and we haven't navigated
    expect(eventsApi.createEvent).toHaveBeenCalledTimes(1);
    expect(mockNavigate).not.toHaveBeenCalled();

    // Second click: Retry should succeed
    fireEvent.click(screen.getByText(/Publish event/i));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/organiser/dashboard', expect.anything()));
    
    // The critical assertion: createEvent STILL only called once
    expect(eventsApi.createEvent).toHaveBeenCalledTimes(1);
  });

  it('does not create a duplicate draft when publication fails then retries', async () => {
    eventsApi.createEvent.mockResolvedValue({ id: 'draft-456' });

    // Attempt 1: Seating succeeds, Publish fails.
    // Attempt 2: PUT update succeeds, seating succeeds, publish succeeds.
    apiClient.request
      .mockResolvedValueOnce({ ok: true }) // Attempt 1: Seating save
      .mockRejectedValueOnce(new Error('publish failed')) // Attempt 1: Publish fails
      .mockResolvedValueOnce({ ok: true }) // Attempt 2: PUT /events/draft-456
      .mockResolvedValueOnce({ ok: true }) // Attempt 2: Seating save
      .mockResolvedValueOnce({ ok: true }); // Attempt 2: Publish

    render(<MemoryRouter><EventCreationWizard /></MemoryRouter>);
    await fillWizard();

    // First click: Should fail
    fireEvent.click(screen.getByText(/Publish event/i));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(eventsApi.createEvent).toHaveBeenCalledTimes(1);

    // Second click: Retry
    fireEvent.click(screen.getByText(/Publish event/i));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalled());
    expect(eventsApi.createEvent).toHaveBeenCalledTimes(1);
  });
});