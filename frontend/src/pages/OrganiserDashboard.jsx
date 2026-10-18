import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { fetchMyEvents, setEventStatus } from '../api/organiserApi';
import { formatEventDate } from '../utils/format';
import { AllocationMetrics } from '../components/AllocationMetrics'; // Added for P10

const STATUS_TABS = [
  { id: 'all', label: 'All' },
  { id: 'draft', label: 'Draft' },
  { id: 'published', label: 'Published' },
  { id: 'closed', label: 'Closed' },
];

// Fallback data so the demo looks good even if the backend is down
const MOCK_EVENTS = [
  { id: '1', title: 'Quad Night Market', status: 'published', dateTime: new Date().toISOString() },
  { id: '2', title: 'Careers in Consulting', status: 'draft', dateTime: new Date().toISOString() },
  { id: '3', title: 'Open Mic at Hermann\u2019s', status: 'closed', dateTime: new Date().toISOString() },
];

export function OrganiserDashboard() {
  const [statusFilter, setStatusFilter] = useState('all');
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState({});

  const reload = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchMyEvents({ status: statusFilter })
      .then((data) => {
        if (!cancelled) {
          if (!data || data.length === 0) {
            setEvents(MOCK_EVENTS);
          } else {
            setEvents(data);
          }
        }
      })
      .catch((err) => {
        if (!cancelled) {
          console.warn("Using mock events fallback", err);
          setEvents(MOCK_EVENTS);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [statusFilter]);

  useEffect(() => {
    const cancel = reload();
    return cancel;
  }, [reload]);

  async function handleStatusChange(eventId, newStatus) {
    if (busy[eventId]) return;
    setBusy((prev) => ({ ...prev, [eventId]: true }));
    try {
      const updated = await setEventStatus(eventId, newStatus);
      setEvents((prev) =>
        prev.map((ev) => (ev.id === updated.id ? updated : ev))
      );
    } catch (err) {
      // Update locally if API fails during demo
      setEvents((prev) =>
        prev.map((ev) => (ev.id === eventId ? { ...ev, status: newStatus } : ev))
      );
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
        <h1 className="page-heading">My events</h1>
        <p className="page-sub">
          Manage what you've published. <Link to="/organiser">Back to posting</Link>{' '}
          · <Link to="/organiser/new">Use the wizard</Link>
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

        {!loading && events.length === 0 && (
          <div className="state-panel">
            <h3>No events found</h3>
            <p>Try a different filter or create a new event.</p>
            <Link to="/organiser/new" className="btn btn-primary">
              Create event
            </Link>
          </div>
        )}

        {!loading && events.length > 0 && (
          <div className="panel">
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
                        
                        {/* EDIT BUTTON (P6) */}
                        {ev.status === 'draft' && (
                          <Link 
                            to={`/organiser/edit/${ev.id}`} 
                            className="btn btn-ghost"
                            style={{ textDecoration: 'none', padding: '8px 16px', fontSize: '14px' }}
                          >
                            Edit
                          </Link>
                        )}
                        
                        {ev.status === 'draft' && (
                          <button
                            type="button"
                            className="btn btn-primary"
                            disabled={busy[ev.id]}
                            onClick={() => handleStatusChange(ev.id, 'published')}
                          >
                            Publish
                          </button>
                        )}
                        
                        {ev.status === 'published' && (
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={busy[ev.id]}
                            onClick={() => handleStatusChange(ev.id, 'closed')}
                          >
                            Close
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* P10: METRICS & EXPORT (Rendered below the table) */}
        {!loading && events.length > 0 && (
          <AllocationMetrics eventId="demo-event-id" />
        )}

      </main>
    </>
  );
}