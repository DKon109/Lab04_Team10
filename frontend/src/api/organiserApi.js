import { request } from './apiClient';

export async function fetchMyEvents({ status } = {}) {
  const qs =
    status && status !== 'all'
      ? `?status=${encodeURIComponent(status)}`
      : '';
  return request(`/organiser/events${qs}`);
}

export async function setEventStatus(eventId, newStatus) {
  return request(`/events/${encodeURIComponent(eventId)}/status`, {
    method: 'PATCH',
    body: { status: newStatus },
  });
}