import { request } from './apiClient';

export const fetchMyBookings = () => request('/auth/me/bookings');
export const mockCheckout = (eventId, body) => request(`/events/${eventId}/mock-checkout`, { method: 'POST', body });
