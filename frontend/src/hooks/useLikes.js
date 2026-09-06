import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createLikesAdapter } from '../services/likesService';

/**
 * Owns the current user's liked-event ids. The heart updates optimistically
 * and reverts if the adapter call fails. `version` bumps on every toggle so
 * the For You feed can re-rank. `countDelta` adjusts displayed like counts
 * in api mode without refetching (local likes never alter shared counts).
 */
export function useLikes(user) {
  const adapter = useMemo(
    () => (user ? createLikesAdapter(user.id) : null),
    [user]
  );
  const [likedIds, setLikedIds] = useState(() => new Set());
  const [countDelta, setCountDelta] = useState({});
  const [version, setVersion] = useState(0);
  const adapterRef = useRef(adapter);
  adapterRef.current = adapter;

  useEffect(() => {
    if (!adapter) return;
    let cancelled = false;
    adapter
      .getLikedIds()
      .then((ids) => {
        if (!cancelled) setLikedIds(new Set(ids));
      })
      .catch(() => {
        // Feed still works without the liked set; hearts start unfilled.
      });
    return () => {
      cancelled = true;
    };
  }, [adapter]);

  const toggleLike = useCallback(
    async (event) => {
      const current = adapterRef.current;
      if (!current) return;
      const wasLiked = likedIds.has(event.id);

      setLikedIds((prev) => {
        const next = new Set(prev);
        if (wasLiked) next.delete(event.id);
        else next.add(event.id);
        return next;
      });
      if (current.mode === 'api') {
        setCountDelta((prev) => ({
          ...prev,
          [event.id]: (prev[event.id] || 0) + (wasLiked ? -1 : 1),
        }));
      }

      try {
        if (wasLiked) await current.unlike(event.id);
        else await current.like(event.id);
        // Re-rank For You only once the like is actually persisted, so a
        // fetch doesn't race the write and show stale recommendations.
        setVersion((v) => v + 1);
      } catch (error) {
        // Revert the optimistic update on failure.
        setLikedIds((prev) => {
          const next = new Set(prev);
          if (wasLiked) next.add(event.id);
          else next.delete(event.id);
          return next;
        });
        if (current.mode === 'api') {
          setCountDelta((prev) => ({
            ...prev,
            [event.id]: (prev[event.id] || 0) + (wasLiked ? 1 : -1),
          }));
        }
        throw error;
      }
    },
    [likedIds]
  );

  return { adapter, likedIds, countDelta, toggleLike, likesVersion: version };
}
