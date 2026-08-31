// Single switch between the temporary local likes mode and the backend API.
// Components never import an adapter directly — flipping VITE_LIKES_MODE is
// the only change needed to move between modes.

import { createLocalLikesAdapter } from './localLikesAdapter';
import { createApiLikesAdapter } from './apiLikesAdapter';

export function getLikesMode() {
  return import.meta.env.VITE_LIKES_MODE === 'local' ? 'local' : 'api';
}

export function createLikesAdapter(userId) {
  return getLikesMode() === 'local'
    ? createLocalLikesAdapter(userId)
    : createApiLikesAdapter();
}
