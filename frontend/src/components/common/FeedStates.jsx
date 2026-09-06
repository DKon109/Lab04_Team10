// Loading, error and empty states for the event feed.

export function SkeletonGrid({ count = 6 }) {
  return (
    <div className="event-grid" role="status" aria-label="Loading events">
      {Array.from({ length: count }).map((_, i) => (
        <div className="skeleton-card" key={i} aria-hidden="true">
          <div className="skeleton-block skeleton-media" />
          <div className="skeleton-block skeleton-line" style={{ width: '60%' }} />
          <div className="skeleton-block skeleton-line" style={{ width: '85%' }} />
          <div className="skeleton-block skeleton-line" style={{ width: '40%' }} />
        </div>
      ))}
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="state-panel" role="alert">
      <h3>Something went wrong</h3>
      <p>{message || 'We could not load events right now.'}</p>
      <button type="button" className="btn btn-primary" onClick={onRetry}>
        Retry
      </button>
    </div>
  );
}

export function EmptyState({ title, children }) {
  return (
    <div className="state-panel">
      <h3>{title}</h3>
      {children}
    </div>
  );
}
