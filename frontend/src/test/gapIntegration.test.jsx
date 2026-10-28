import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { VenuePicker } from '../components/VenuePicker';
import { AllocationMetrics } from '../components/AllocationMetrics';
import * as apiClient from '../api/apiClient';

vi.mock('../api/apiClient', () => ({ request: vi.fn() }));

// Mock ManagedSeatMap to assert it gets rendered
vi.mock('../components/ManagedSeatMap', () => ({
  ManagedSeatMap: ({ map }) => <div data-testid="managed-map">Managed Map Elements: {map.elements.length}</div>
}));

describe('Ryoji Final Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // 1. RYOJI'S POINT 1: Render actual campus map with irregular layout
  it('renders ManagedSeatMap for campus templates and passes the actual map data', async () => {
    // Mock an irregular layout (different number of seats per row)
    apiClient.request.mockResolvedValue([
      {
        id: 'everest',
        name: 'Everest Theatre',
        sourceUrl: 'http://example.com/map.pdf',
        map: {
          elements: [
            { seats: [1, 2, 3] }, // 3 seats
            { seats: [4, 5] },    // 2 seats
            { seats: [6, 7, 8, 9] } // 4 seats
          ]
        }
      }
    ]);

    render(<MemoryRouter><VenuePicker value={null} onSelect={vi.fn()} /></MemoryRouter>);

    await waitFor(() => expect(screen.queryByText(/Loading rooms/i)).not.toBeInTheDocument());

    // Click the room to select it
    fireEvent.click(screen.getByText(/Everest Theatre/i));

    // Assert that the ManagedSeatMap is rendered with the correct data (3 elements)
    expect(screen.getByTestId('managed-map')).toHaveTextContent('Managed Map Elements: 3');
    // Ensure generic LayoutPreview is NOT rendered
    expect(screen.queryByText('Layout preview')).toBeInTheDocument(); // The wrapper is still there
    expect(screen.getByTestId('managed-map')).toBeInTheDocument();
  });

  // 2. RYOJI'S POINT 2: Ignore stale metrics responses
  it('ignores stale metrics responses when eventId changes mid-request', async () => {
    let resolveEventA;
    const promiseA = new Promise((resolve) => { resolveEventA = resolve; });

    // Mock request so it hangs for Event A
    apiClient.request.mockReturnValueOnce(promiseA);

    const { unmount } = render(<AllocationMetrics eventId="A" />);
    
    // Click fetch for Event A (hangs)
    fireEvent.click(screen.getByText(/View Allocation Metrics/i));

    // Unmount (simulating the dashboard completely switching the component)
    unmount();

    // Render fresh for Event B
    apiClient.request.mockResolvedValueOnce({
      groupCount: 99, // B's data
      percentageGroupsTogether: 100,
      elapsedMillis: 50
    });
    
    render(<AllocationMetrics eventId="B" />);
    fireEvent.click(screen.getByText(/View Allocation Metrics/i));

    // Wait for B to load
    await waitFor(() => {
      expect(screen.getByText('99')).toBeInTheDocument();
    });

    // Now resolve the stale Event A request
    resolveEventA({
      groupCount: 1, // A's stale data
      percentageGroupsTogether: 50,
      elapsedMillis: 10
    });

    // Assert that A's stale data DID NOT overwrite B's data
    await waitFor(() => {
      expect(screen.queryByText('1')).not.toBeInTheDocument();
      expect(screen.getByText('99')).toBeInTheDocument();
    });
  });
});