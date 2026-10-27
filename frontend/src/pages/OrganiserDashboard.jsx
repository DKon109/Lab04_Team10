import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { fetchMyEvents, setEventStatus } from '../api/organiserApi';
import { formatEventDate } from '../utils/format';
import { AllocationMetrics } from '../components/AllocationMetrics';

const STATUS_TABS = [
  { id: 'all', label: 'All' },
  { id: 'draft', label: 'Draft' },
  { id: 'published', label: 'Published' },
  { id: 'closed', label: 'Closed' },
];

export function OrganiserDashboard() {
  const [statusFilter, setStatusFilter] = useState('all');
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState({});
  
  // GAP 1 FIX: State for selecting which event's metrics to view
  const [selectedMetricsEventId, setSelectedMetricsEventId] = useState('');

  const reload = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchMyEvents({ status: statusFilter })
      .then((data) => {
        if (!cancelled) {
          setEvents(data || []);
          // Auto-select the first event if none is selected
          if (data && data.length > 0 && !selectedMetricsEventId) {
            setSelectedMetricsEventId(data[0].id.toString());
          }
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'Failed to load events');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [statusFilter, selectedMetricsEventId]);

  useEffect(() => {
    const cancel = reload();
    return cancel;
  }, [reload]);

  async function handleStatusChange(eventId, newStatus) {
    if (busy[eventId]) return;
    setBusy((prev) => ({ ...prev, [eventId]: true }));
    try {
      const updated = await setEventStatus(eventId, newStatus);
      setEvents((prev) => prev.map((ev) => (ev.id === updated.id ? updated : ev)));
    } catch (err) {
      alert(err.message || 'Could not update the event status.');
    } finally {
      setBusy((prev) => {
        const next = { ...prev };
        delete next[eventId];
        return next;
      });
    }
  }

  return (
    <>
      <TopNav />
      <main className="page">
        <p className="eyebrow">ORGANISER WORKSPACE</p>
        <h1 className="page-heading">Your events, from first idea to final seat.</h1>
        <p className="page-sub">
          Manage your drafts, venue layouts and allocation. <Link to="/organiser/new" className="btn btn-primary">Create event +</Link>
        </p>

        <div className="segmented" role="tablist" aria-label="Filter by status">
          {STATUS_TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={statusFilter === t.id}
              onClick={() => setStatusFilter(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>

        {loading && <p className="results-note">Loading…</p>}
        
        {error && (
          <div className="state-panel" style={{ borderColor: 'var(--danger)' }}>
            <h3>Error loading events</h3>
            <p>{error}</p>
            <button className="btn btn-primary" onClick={reload}>Try Again</button>
          </div>
        )}

        {!loading && !error && events.length === 0 && (
          <div className="state-panel">
            <h3>No events found</h3>
            <p>Try a different filter or create a new event.</p>
            <Link to="/organiser/new" className="btn btn-primary">Create event</Link>
          </div>
        )}

        {!loading && !error && events.length > 0 && (
          <div className="panel dashboard-table">
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--line)' }}>
                  <th style={{ padding: '10px 8px' }}>Title</th>
                  <th style={{ padding: '10px 8px' }}>Status</th>
                  <th style={{ padding: '10px 8px' }}>Date</th>
                  <th style={{ padding: '10px 8px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {events.map((ev) => (
                  <tr key={ev.id} style={{ borderBottom: '1px solid var(--line)' }}>
                    <td style={{ padding: '12px 8px' }}>{ev.title}</td>
                    <td style={{ padding: '12px 8px' }}>
                      <span className="badge badge-context">{ev.status}</span>
                    </td>
                    <td style={{ padding: '12px 8px' }}>
                      {formatEventDate(ev.dateTime)}
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                        {ev.status === 'draft' && (
                          <Link to={`/organiser/events/${ev.id}/edit`} className="btn btn-ghost" style={{ textDecoration: 'none', padding: '8px 16px', fontSize: '14px' }}>Edit</Link>
                        )}
                        {ev.status === 'draft' && (
                          <button type="button" className="btn btn-primary" disabled={busy[ev.id]} onClick={() => handleStatusChange(ev.id, 'published')}>Publish</button>
                        )}
                        {ev.status === 'published' && (
                          <button type="button" className="btn btn-ghost" disabled={busy[ev.id]} onClick={() => handleStatusChange(ev.id, 'closed')}>Close</button>
                        )}
                        <Link className="btn btn-primary" to={`/organiser/events/${ev.id}`}>Manage event →</Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* GAP 1 FIX: Event Selector for Metrics */}
        {!loading && !error && events.length > 0 && (
          <div style={{ marginTop: '30px', padding: '20px', background: 'var(--bg)', borderRadius: '8px', border: '1px solid var(--line)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '15px', marginBottom: '10px' }}>
              <label htmlFor="metrics-event-select" style={{ fontWeight: 'bold' }}>Select Event for Metrics:</label>
              <select
                id="metrics-event-select"
                value={selectedMetricsEventId}
                onChange={(e) => setSelectedMetricsEventId(e.target.value)}
                style={{ padding: '8px', borderRadius: '4px', border: '1px solid var(--line)' }}
              >
                <option value="" disabled>Select an event</option>
                {events.map(ev => (
                  <option key={ev.id} value={ev.id}>{ev.title}</option>
                ))}
              </select>
            </div>
            
            {selectedMetricsEventId && (
              <AllocationMetrics eventId={selectedMetricsEventId} />
            )}
          </div>
        )}
      </main>
    </>
  );
}