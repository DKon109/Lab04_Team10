import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { fetchMyEvents } from '../api/organiserApi';
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

  return (
    <>
      <TopNav />
      <main className="page">
        <p className="eyebrow">ORGANISER WORKSPACE</p><h1 className="page-heading">Your events, from first idea to final seat.</h1>
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
          <div className="panel dashboard-table">
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
                      <Link className="btn btn-primary" to={`/organiser/events/${ev.id}`}>Manage event →</Link>
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
