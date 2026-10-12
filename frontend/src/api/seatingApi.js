import { request } from './apiClient';
export const fetchSeating = id => request(`/organiser/events/${id}/seating`);
export const saveSeating = (id, body) => request(`/organiser/events/${id}/seating`, { method: 'PUT', body });
export const allocateSeats = id => request(`/organiser/events/${id}/allocate`, { method: 'POST' });
export const fetchSeatingProfiles = () => request('/organiser/seating-profiles');
export const fetchTicket = id => request(`/bookings/${id}/ticket`);
export const fetchAllocationExport = id => request(`/organiser/events/${id}/allocation/export`, { responseType: 'text' });
