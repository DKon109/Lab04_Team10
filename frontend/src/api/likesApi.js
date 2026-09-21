import { request } from './apiClient';

export function likeEvent(eventId) {
  return request(`/events/${eventId}/like`, { method: 'POST' });
}

export function unlikeEvent(eventId) {
  return request(`/events/${eventId}/like`, { method: 'DELETE' });
}

export function fetchLikedEvents() {
  return request('/users/me/liked-events');
}

export function fetchForYou() {
  return request('/events/for-you');
}
