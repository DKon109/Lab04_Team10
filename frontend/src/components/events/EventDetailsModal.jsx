import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { EventImage } from '../common/EventImage';
import { formatEventDate } from '../../utils/format';

/**
 * Accessible details dialog: focus moves to the close button on open,
 * Escape and backdrop click close it, focus returns to the opener, and
 * Tab is kept inside the dialog while it's open.
 */
export function EventDetailsModal({ event, liked, likeCount, onToggleLike, onClose }) {
  const dialogRef = useRef(null);
  const closeRef = useRef(null);

  useEffect(() => {
    const opener = document.activeElement;
    closeRef.current?.focus();
    document.body.style.overflow = 'hidden';

    function onKeyDown(e) {
      if (e.key === 'Escape') {
        onClose();
        return;
      }
      if (e.key === 'Tab') {
        const focusables = dialogRef.current?.querySelectorAll(
          'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled)'
        );
        if (!focusables || focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = '';
      opener?.focus?.();
    };
  }, [onClose]);

  if (!event) return null;
  const showCount = typeof likeCount === 'number';

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="event-modal-title"
        ref={dialogRef}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="media">
          <EventImage src={event.imageUrl} alt="" title={event.title} />
          <button
            type="button"
            className="close-button"
            onClick={onClose}
            aria-label="Close event details"
            ref={closeRef}
          >
            ✕
          </button>
        </div>
        <div className="modal-body">
          <span className="badge badge-context">
            {event.university || event.city || 'Sydney'}
          </span>
          <h2 id="event-modal-title">{event.title}</h2>
          {event.description && <p>{event.description}</p>}

          <dl className="detail-list">
            <dt>When</dt>
            <dd>{formatEventDate(event.dateTime)}</dd>
            <dt>Venue</dt>
            <dd>{event.venue}</dd>
            <dt>Organiser</dt>
            <dd>
              {event.organiserName}
              {event.organiserType ? ` (${event.organiserType.toLowerCase()})` : ''}
            </dd>
            <dt>Category</dt>
            <dd>{event.category}</dd>
            {(event.tags || []).length > 0 && (
              <>
                <dt>Tags</dt>
                <dd>{event.tags.join(', ')}</dd>
              </>
            )}
          </dl>

          <div className="modal-booking"><p>{event.seatIdentifiersConfirmed ? 'Make it a plan. Book solo or with a private group.' : 'Make it a plan. Reserve your individual place.'}</p>
            {event.registrationStatus === 'CLOSED' || new Date(event.dateTime) <= new Date() ? <p role="status">Registration unavailable</p> : <Link className="btn btn-primary" to={`/events/${event.id}/book`}>Book this event →</Link>}
          </div>

          <button
            type="button"
            className="like-button"
            aria-pressed={liked}
            aria-label={liked ? `Unlike ${event.title}` : `Like ${event.title}`}
            onClick={() => onToggleLike(event)}
          >
            <span aria-hidden="true">{liked ? '♥' : '♡'}</span>
            {liked ? 'Liked' : 'Like'}
            {showCount ? ` · ${likeCount}` : ''}
          </button>
        </div>
      </div>
    </div>
  );
}
