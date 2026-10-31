import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { VenuePicker } from '../components/VenuePicker';
import { AllocationMetrics } from '../components/AllocationMetrics';
import * as apiClient from '../api/apiClient';

vi.mock('../api/apiClient', () => ({ request: vi.fn() }));

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
          // Test with no version
          elements: [
            { seats: [{ id: '1' }, { id: '2', blocked: true }, { id: '3', privateSeat: true }] }
          ]
        }
      }
    ]);

    render(<MemoryRouter><VenuePicker value={null} onSelect={vi.fn()} /></MemoryRouter>);

    await waitFor(() => expect(screen.queryByText(/Loading rooms/i)).not.toBeInTheDocument());

    // Capacity should be 1 (only the first seat is valid)
    expect(screen.getByText(/1 seats/i)).toBeInTheDocument();

    fireEvent.click(screen.getByText(/Everest/i));

    // Assert correct status mapping and no 'undefined' version string
    expect(screen.getByTestId('managed-map')).toHaveTextContent('Map Version: undefined | Seats: AVAILABLE,BLOCKED,PRIVATE');
  });

  it('ignores stale metrics using key prop (rerender)', async () => {
    let resolveA;
    const promiseA = new Promise(r => resolveA = r);
    apiClient.request.mockReturnValueOnce(promiseA);

    // Render Event A (with key)
    const { rerender } = render(<AllocationMetrics key="A" eventId="A" />);
    fireEvent.click(screen.getByText(/View Allocation Metrics/i));

    // Rerender with Event B BEFORE A resolves
    apiClient.request.mockResolvedValueOnce({ groupCount: 99, elapsedMillis: 50 });
    rerender(<AllocationMetrics key="B" eventId="B" />);

    // B should load successfully
    fireEvent.click(screen.getByText(/View Allocation Metrics/i));
    await waitFor(() => expect(screen.getByText('99')).toBeInTheDocument());

    // Now resolve A
    resolveA({ groupCount: 1, elapsedMillis: 10 });

    // Assert A's data didn't overwrite B's
    await waitFor(() => {
      expect(screen.queryByText('1')).not.toBeInTheDocument();
      expect(screen.getByText('99')).toBeInTheDocument();
    });
  });
});