import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { VenuePicker } from '../components/VenuePicker';
import { AllocationMetrics } from '../components/AllocationMetrics';
import { EventCreationWizard } from '../pages/EventCreationWizard';
import * as apiClient from '../api/apiClient';
import * as eventsApi from '../api/eventsApi';

vi.mock('../context/AuthContext', () => ({
  AuthProvider: ({ children }) => <div>{children}</div>,
  useAuth: () => ({ user: { id: 1, role: 'COMMITTEE_MEMBER' }, logout: vi.fn() }),
}));

vi.mock('../api/apiClient', () => ({ request: vi.fn() }));
vi.mock('../api/eventsApi', () => ({ createEvent: vi.fn() }));

vi.mock('../components/layout/TopNav', () => ({ TopNav: () => <nav data-testid="topnav" /> }));

vi.mock('../components/ManagedSeatMap', () => ({
  ManagedSeatMap: ({ map }) => <div data-testid="managed-map">Map Version: {map.version || 'undefined'} | Seats: {map.elements[0].seats.map(s => s.status).join(',')}</div>
}));

describe('Final Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calculates capacity excluding blocked/private and maps seat status', async () => {
    apiClient.request.mockResolvedValue([
      {
        id: 'everest',
        name: 'Everest',
        map: {
          elements: [
            { seats: [{ id: '1' }, { id: '2', blocked: true }, { id: '3', privateSeat: true }] }
          ]
        }
      }
    ]);

    render(<MemoryRouter><VenuePicker value={null} onSelect={vi.fn()} /></MemoryRouter>);

    await waitFor(() => expect(screen.queryByText(/Loading rooms/i)).not.toBeInTheDocument());

    expect(screen.getByText(/1 seats/i)).toBeInTheDocument();

    fireEvent.click(screen.getByText(/Everest/i));

    expect(screen.getByTestId('managed-map')).toHaveTextContent('Map Version: undefined | Seats: AVAILABLE,BLOCKED,PRIVATE');
  });

  it('ignores stale metrics using key prop (rerender)', async () => {
    let resolveA;
    const promiseA = new Promise(r => resolveA = r);
    apiClient.request.mockReturnValueOnce(promiseA);

    const { rerender } = render(<AllocationMetrics key="A" eventId="A" />);
    fireEvent.click(screen.getByText(/View Allocation Metrics/i));

    apiClient.request.mockResolvedValueOnce({ groupCount: 99, elapsedMillis: 50 });
    rerender(<AllocationMetrics key="B" eventId="B" />);

    fireEvent.click(screen.getByText(/View Allocation Metrics/i));
    await waitFor(() => expect(screen.getByText('99')).toBeInTheDocument());

    resolveA({ groupCount: 1, elapsedMillis: 10 });

    await waitFor(() => {
      expect(screen.queryByText('1')).not.toBeInTheDocument();
      expect(screen.getByText('99')).toBeInTheDocument();
    });
  });

  it('excludes seats inside private elements from capacity (P2)', async () => {
    apiClient.request.mockResolvedValue([
      {
        id: 'everest',
        name: 'Everest',
        map: {
          elements: [
            { seats: [{ id: '1' }, { id: '2' }] }, // Normal element
            { privateElement: true, seats: [{ id: '3' }, { id: '4' }] } // Private element
          ]
        }
      }
    ]);

    render(<MemoryRouter><VenuePicker value={null} onSelect={vi.fn()} /></MemoryRouter>);

    await waitFor(() => expect(screen.queryByText(/Loading rooms/i)).not.toBeInTheDocument());

    // Capacity should be 2 (only the normal element's seats)
    expect(screen.getByText(/2 seats/i)).toBeInTheDocument();
  });

  it('forces identifiersConfirmed to false for General Admission with numbered external venues (P1)', async () => {
    eventsApi.createEvent.mockResolvedValue({ id: 'draft-ga' });
    
    // Mock API responses
    apiClient.request.mockImplementation((url) => {
      if (url.includes('venue-templates')) return Promise.resolve([]);
      return Promise.resolve({ ok: true });
    });

    render(<MemoryRouter><EventCreationWizard /></MemoryRouter>);

    // Fill Step 1
    fireEvent.change(screen.getByLabelText(/Title/i), { target: { value: 'GA Event' } });
    fireEvent.change(screen.getByLabelText(/Category/i), { target: { value: 'Tech' } });
    fireEvent.click(screen.getByText('Next'));

    // Fill Step 2
    fireEvent.change(screen.getByLabelText(/Date and time/i), { target: { value: '2026-12-01T10:00' } });
    fireEvent.change(screen.getByLabelText(/Venue name/i), { target: { value: 'Test Venue' } });
    fireEvent.click(screen.getByText('Next'));

    // Step 3: Select External Template, enable numbered seats, choose GA
    await waitFor(() => expect(screen.queryByText(/Loading rooms/i)).not.toBeInTheDocument());
    
    fireEvent.click(screen.getByText('External template'));
    fireEvent.click(screen.getByText('Front stage'));
    
    // Make it a numbered venue
    fireEvent.click(screen.getByLabelText(/Seats are numbered/i));
    
    // Choose General admission
    fireEvent.click(screen.getByLabelText(/General admission/i));
    fireEvent.click(screen.getByText('Next'));

    // Fill Step 4
    fireEvent.change(screen.getByLabelText(/Organiser name/i), { target: { value: 'Test Society' } });
    fireEvent.click(screen.getByText('Next'));

    // Publish
    fireEvent.click(screen.getByText(/Publish event/i));

    await waitFor(() => {
      expect(apiClient.request).toHaveBeenCalledWith(
        expect.stringContaining('/organiser/events/draft-ga/seating'),
        expect.objectContaining({
          method: 'PUT',
          body: expect.objectContaining({
            identifiersConfirmed: false
          })
        })
      );
    });
  });
});