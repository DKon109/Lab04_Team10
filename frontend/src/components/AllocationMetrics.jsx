import { useState } from 'react';
import { request } from '../api/apiClient';

export function AllocationMetrics({ eventId }) {
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState(null);

  // Fallback data matching Rafly's AllocationEvaluation record
  const mockMetrics = {
    groupCount: 30,
    splitGroupCount: 2,
    percentageGroupsTogether: 95.0,
    isolatedSeatCount: 4,
    duplicateAllocationCount: 0
  };

  const fetchMetrics = async () => {
    setLoading(true);
    try {
      // Matches Rafly's @GetMapping("/api/organiser/events/{id}/allocation/metrics")
      const data = await request(`/organiser/events/${eventId}/allocation/metrics`);
      setMetrics(data);
    } catch (err) {
      console.warn("Metrics API not ready, using mock data");
      setMetrics(mockMetrics); 
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    try {
      // Matches Rafly's @GetMapping("/api/organiser/events/{id}/allocation/export")
      const csvData = await request(`/organiser/events/${eventId}/allocation/export`);
      
      // Create a blob and download it
      const blob = new Blob([csvData], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `anonymous_seating_${eventId}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      console.warn("Export API not ready, using mock CSV");
      const csvContent = "data:text/csv;charset=utf-8,BookingRef,Seat\nDemo1,A1";
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `mock_seating_${eventId}.csv`);
      document.body.appendChild(link);
      link.click();
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
        <div><strong>Groups Evaluated:</strong> {metrics.groupCount}</div>
        <div><strong>Groups Kept Together:</strong> {metrics.percentageGroupsTogether}%</div>
        <div><strong>Split Groups:</strong> {metrics.splitGroupCount}</div>
        <div><strong>Isolated Seats:</strong> {metrics.isolatedSeatCount}</div>
        <div><strong>Duplicates:</strong> {metrics.duplicateAllocationCount}</div>
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