import { request } from './apiClient';

/**
 * Builds the query string for GET /api/events.
 * "ALL" (All Sydney) sends no scope parameter — the backend treats
 * missing/ALL/SYDNEY identically. organiserType only applies to USYD.
 */
export function buildEventsQuery({ scope, organiserType, category, date } = {}) {
  const params = new URLSearchParams();
  if (scope && scope !== 'ALL') params.set('scope', scope);
  if (organiserType) params.set('organiserType', organiserType);
  if (category) params.set('category', category);
  if (date) params.set('date', date);
  const query = params.toString();
  return query ? `?${query}` : '';
}

export function fetchEvents(filters) {
  return request(`/events${buildEventsQuery(filters)}`);
}

export function fetchEvent(eventId) {
  return request(`/events/${eventId}`);
}

/** Organiser-only: requires a COMMITTEE_MEMBER account. */
export function createEvent(payload) {
  return request('/events', { method: 'POST', body: payload });
}
