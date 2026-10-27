import { useState, useEffect } from 'react';
import { request } from '../api/apiClient';

export function AllocationMetrics({ eventId }) {
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState(null);
  const [error, setError] = useState(null);

  // GAP 1 FIX: Reset metrics if the selected event changes so stale data doesn't show
  useEffect(() => {
    setMetrics(null);
    setError(null);
  }, [eventId]);

  const fetchMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await request(`/organiser/events/${eventId}/allocation/metrics`);
      setMetrics(data);
    } catch (err) {
      setError(err.message || 'Failed to load metrics');
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    try {
      const res = await fetch(`/api/organiser/events/${eventId}/allocation/export`, {
        headers: {
          'Accept': 'text/csv',
          'Authorization': `Bearer ${sessionStorage.getItem('tixora.token')}`
        }
      });
      if (!res.ok) throw new Error('Export failed');
      
      const csvText = await res.text();
      const blob = new Blob([csvText], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `anonymous_seating_${eventId}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      alert("Export failed: " + err.message);
    }
  };

  if (!metrics && !loading && !error) {
    return (
      <div style={{ marginTop: '20px' }}>
        <button className="btn btn-ghost" onClick={fetchMetrics}>View Allocation Metrics</button>
      </div>
    );
  }

  return (
    <div style={{ marginTop: '20px', padding: '16px', background: 'var(--bg)', borderRadius: '8px', border: '1px solid var(--line)' }}>
      <h3 style={{ marginTop: 0 }}>Allocation Metrics</h3>
      
      {loading && <p>Loading metrics...</p>}
      
      {error && (
        <div style={{ color: 'var(--danger)', marginBottom: '10px' }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      {metrics && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginTop: '10px', fontSize: '14px' }}>
          <div><strong>Groups Evaluated:</strong> {metrics.groupCount}</div>
          <div><strong>Groups Kept Together:</strong> {metrics.percentageGroupsTogether != null ? `${metrics.percentageGroupsTogether}%` : 'Not applicable'}</div>
          <div><strong>Split Groups:</strong> {metrics.splitGroupCount}</div>
          <div><strong>Isolated Seats:</strong> {metrics.isolatedSeatCount}</div>
          <div><strong>Duplicates:</strong> {metrics.duplicateAllocationCount}</div>
          
          {/* GAP 2 FIX: New metrics from AllocationMetricsResponse */}
          <div><strong>Avg Split Distance:</strong> {metrics.averageSplitDistance != null ? metrics.averageSplitDistance : 'Not applicable'}</div>
          <div><strong>Avg Group Contiguity:</strong> {metrics.averageGroupContiguityPercentage != null ? `${metrics.averageGroupContiguityPercentage}%` : 'Not applicable'}</div>
          <div><strong>Runtime:</strong> {metrics.elapsedMillis != null ? `${metrics.elapsedMillis} ms` : 'Not applicable'}</div>
        </div>
      )}

      <div style={{ display: 'flex', gap: '10px', marginTop: '16px' }}>
        <button className="btn btn-primary" onClick={handleExport} style={{ padding: '8px 16px' }} disabled={!metrics}>
          Export Anonymous Seating Plan (CSV)
        </button>
      </div>
    </div>
  );
}