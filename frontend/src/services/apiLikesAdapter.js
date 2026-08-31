// Backend-integrated likes adapter (VITE_LIKES_MODE=api). Uses the real
// endpoints; likes are shared and likeCount values are genuine.

import { likeEvent, unlikeEvent, fetchLikedEvents, fetchForYou } from '../api/likesApi';

export function createApiLikesAdapter() {
  return {
    mode: 'api',
    async getLikedIds() {
      const events = await fetchLikedEvents();
      return events.map((e) => e.id);
    },
    like: likeEvent,
    unlike: unlikeEvent,
    async getForYou() {
      return fetchForYou();
    },
  };
}
