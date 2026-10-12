import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export const FEED_TABS = [
  { id: 'ALL', label: 'All Sydney' },
  { id: 'USYD', label: 'USYD' },
  { id: 'FORYOU', label: 'For You' },
];

export function TopNav({ activeTab, onTabChange }) {
  const { user, logout } = useAuth();

  return (
    <header className="top-nav">
      <div className="top-nav-inner">
        <Link to="/events" className="brand wordmark">
          Tix<span className="tick">o</span>ra
        </Link>

        {onTabChange && (
          <nav className="nav-tabs" aria-label="Event feeds">
            {FEED_TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                aria-current={activeTab === tab.id}
                onClick={() => onTabChange(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        )}

        <div className="nav-user">
          {!onTabChange && <Link to="/events" className="organiser-link">Discover</Link>}
          <Link to="/bookings" className="organiser-link">My bookings</Link>
          {user?.role === 'COMMITTEE_MEMBER' && (
            <Link to="/organiser" className="organiser-link">
              Organiser dashboard
            </Link>
          )}
          <span className="avatar" title={user?.email} aria-hidden="true">
            {user?.email?.charAt(0) || '?'}
          </span>
          <span className="visually-hidden">Signed in as {user?.email}</span>
          <button type="button" className="btn btn-ghost" onClick={logout}>
            Log out
          </button>
        </div>
      </div>
    </header>
  );
}
