import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { fetchMyEvents, setEventStatus } from '../api/organiserApi';
import { formatEventDate } from '../utils/format';

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

  const reload = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchMyEvents({ status: statusFilter })
      .then((data) => {
        if (!cancelled) setEvents(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
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
      alert(err.message || 'Could not update the event.');
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

        {error && (
          <div className="state-panel">
            <h3>Couldn't load your events</h3>
            <p>{error.message}</p>
            <button type="button" className="btn btn-primary" onClick={reload}>
              Try again
            </button>
          </div>
        )}

        {!loading && !error && events.length === 0 && (
          <div className="state-panel">
            <h3>
              {statusFilter === 'all'
                ? "You haven't created any events yet"
                : `No ${statusFilter} events`}
            </h3>
            <p>
              {statusFilter === 'all'
                ? 'Create your first event to see it here.'
                : 'Try a different tab, or create a new one.'}
            </p>
            <Link to="/organiser/new" className="btn btn-primary">
              Create event
            </Link>
          </div>
        )}

        {!loading && !error && events.length > 0 && (
          <div className="panel">
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--line)' }}>
                  <th style={{ padding: '10px 8px' }}>Title</th>
                  <th style={{ padding: '10px 8px' }}>Status</th>
                  <th style={{ padding: '10px 8px' }}>Date</th>
                  <th style={{ padding: '10px 8px' }}></th>
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
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </>
  );
}
