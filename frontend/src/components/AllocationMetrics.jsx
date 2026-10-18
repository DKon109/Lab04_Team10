import { useState } from 'react';
import { request } from '../api/apiClient';

export function AllocationMetrics({ eventId }) {
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState(null);

  // Fallback data based on Raffy's evaluation measures
  const mockMetrics = {
    groupsKeptTogether: 95,
    splitGroups: 2,
    isolatedSeats: 4,
    executionTimeMs: 1250,
    totalAllocated: 298
  };

  const fetchMetrics = async () => {
    setLoading(true);
    try {
      // Assuming Raffy names the endpoint /metrics
      const data = await request(`/events/${eventId}/metrics`);
      setMetrics(data);
    } catch (err) {
      console.warn("Metrics API not ready, using mock data for demo");
      setMetrics(mockMetrics); // Fallback for the demo
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    try {
      // In the future: const csvData = await request(`/events/${eventId}/export`);
      // For now, generate a fake CSV to show the UI works
      const csvContent = "data:text/csv;charset=utf-8,BookingRef,Seat,R1-S1,R1-S2\nDemo1,A1,A2";
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `anonymous_seating_${eventId}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      alert("Export failed: " + err.message);
    }
  };

  if (!metrics) {
    return (
      <div style={{ marginTop: '20px' }}>
        <button className="btn btn-ghost" onClick={fetchMetrics} disabled={loading}>
          {loading ? 'Loading metrics...' : 'View Allocation Metrics'}
        </button>
      </div>
    );
  }

  return (
    <div style={{ marginTop: '20px', padding: '16px', background: 'var(--bg)', borderRadius: '8px', border: '1px solid var(--line)' }}>
      <h3 style={{ marginTop: 0 }}>Allocation Metrics</h3>
      
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginTop: '10px', fontSize: '14px' }}>
        <div><strong>Total Allocated:</strong> {metrics.totalAllocated}</div>
        <div><strong>Execution Time:</strong> {metrics.executionTimeMs}ms</div>
        <div><strong>Groups Kept Together:</strong> {metrics.groupsKeptTogether}%</div>
        <div><strong>Split Groups:</strong> {metrics.splitGroups}</div>
        <div><strong>Isolated Seats:</strong> {metrics.isolatedSeats}</div>
      </div>

      <button 
        className="btn btn-primary" 
        onClick={handleExport}
        style={{ marginTop: '16px', padding: '8px 16px' }}
      >
        Export Anonymous Seating Plan (CSV)
      </button>
    </div>
  );
}