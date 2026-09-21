import { useCallback, useEffect, useState } from 'react';
import { fetchEvents } from '../api/eventsApi';

/**
 * Loads the feed for the active tab. ALL/USYD hit GET /api/events (with the
 * organiserType filter for USYD); FORYOU goes through the likes adapter so
 * local and api modes stay interchangeable. Category, date and search are
 * applied client-side by the page — the option lists stay derived from the
 * full tab dataset that way.
 */
export function useEvents(tab, organiserType, { adapter, likesVersion }) {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  const retry = useCallback(() => setAttempt((a) => a + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    const load =
      tab === 'FORYOU'
        ? adapter.getForYou(() => fetchEvents({ scope: 'ALL' }))
        : fetchEvents({ scope: tab, organiserType: tab === 'USYD' ? organiserType : '' });

    load
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
    // likesVersion re-ranks For You after a like/unlike.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, organiserType, adapter, attempt, tab === 'FORYOU' ? likesVersion : 0]);

  return { events, loading, error, retry };
}
