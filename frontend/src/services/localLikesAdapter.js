// TEMPORARY frontend-only likes adapter (VITE_LIKES_MODE=local).
// Persists likes per user in localStorage and ranks "For You" in the browser
// so the frontend can be demonstrated against a backend build that does not
// yet have the like endpoints. This is an integration stopgap, not the
// completed full-stack feature — see INTEGRATION_NOTES.md.

import { rankForYou } from './recommendationService';

export function createLocalLikesAdapter(userId) {
  const storageKey = `tixora.likes.${userId}`;

  function read() {
    try {
      const ids = JSON.parse(localStorage.getItem(storageKey));
      return Array.isArray(ids) ? ids : [];
    } catch {
      return [];
    }
  }

  function write(ids) {
    localStorage.setItem(storageKey, JSON.stringify(ids));
  }

  return {
    mode: 'local',
    async getLikedIds() {
      return read();
    },
    async like(eventId) {
      const ids = read();
      if (!ids.includes(eventId)) write([...ids, eventId]);
    },
    async unlike(eventId) {
      write(read().filter((id) => id !== eventId));
    },
    // fetchAllEvents: () => Promise<event[]> — the adapter ranks client-side.
    async getForYou(fetchAllEvents) {
      const events = await fetchAllEvents();
      return rankForYou(events, new Set(read()));
    },
  };
}
