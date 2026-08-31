import { EventImage } from '../common/EventImage';
import { formatEventDate } from '../../utils/format';

/**
 * One feed card. The whole card opens details (button semantics on the
 * title), while the like button stays an independent accessible toggle.
 * likeCount renders only when it's a real number from the backend.
 */
export function EventCard({ event, liked, likeCount, onToggleLike, onOpenDetails }) {
  const showCount = typeof likeCount === 'number';
  const context = event.university ? event.university : event.city || 'Sydney';

  return (
    <article className="event-card">
      <div className="media">
        <EventImage src={event.imageUrl} alt="" title={event.title} />
        <span className="badge badge-category">{event.category}</span>
      </div>

      <div className="body">
        <span className="when">{formatEventDate(event.dateTime)}</span>
        <h3>
          <button
            type="button"
            onClick={() => onOpenDetails(event)}
            style={{
              all: 'unset',
              cursor: 'pointer',
              font: 'inherit',
            }}
          >
            {event.title}
          </button>
        </h3>
        <p className="meta">
          {event.organiserName} · {event.venue}
        </p>
        <div className="tag-row">
          <span className="badge badge-context">{context}</span>
          {(event.tags || []).slice(0, 3).map((tag) => (
            <span className="tag" key={tag}>
              #{tag}
            </span>
          ))}
        </div>

        <div className="actions">
          <button
            type="button"
            className="like-button"
            aria-pressed={liked}
            aria-label={liked ? `Unlike ${event.title}` : `Like ${event.title}`}
            onClick={() => onToggleLike(event)}
          >
            <span aria-hidden="true">{liked ? '♥' : '♡'}</span>
            {showCount ? likeCount : ''}
          </button>
          <button type="button" className="btn btn-ghost" onClick={() => onOpenDetails(event)}>
            View details
          </button>
        </div>
      </div>
    </article>
  );
}
