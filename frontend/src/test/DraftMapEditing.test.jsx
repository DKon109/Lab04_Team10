import { expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { OrganiserPage } from '../pages/OrganiserPage';
import { fetchSeating } from '../api/seatingApi';
import { request } from '../api/apiClient';
vi.mock('../api/seatingApi', () => ({ fetchSeating: vi.fn() }));
vi.mock('../api/apiClient', () => ({ request: vi.fn() }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ user: { role: 'COMMITTEE_MEMBER' }, logout: vi.fn() }) }));

it('can save details of a large mapped draft without resubmitting its map-controlled capacity', async () => {
  fetchSeating.mockResolvedValue({ event: { id: 7, status: 'draft', title: 'Campus theatre', category: 'Arts', dateTime: '2099-12-01T18:00', venue: 'Campus room', organiserName: 'Demo', organiserType: 'SOCIETY', capacity: 788, seatIdentifiersConfirmed: true, tags: [] }, managedMap: { name: 'Campus plan' } });
  request.mockResolvedValue({ id: 7 });
  render(<MemoryRouter initialEntries={['/organiser/events/7/edit']}><Routes><Route path="/organiser/events/:eventId/edit" element={<OrganiserPage />} /><Route path="/organiser/events/7" element={<p>Saved event</p>} /></Routes></MemoryRouter>);
  const title = await screen.findByDisplayValue('Campus theatre');
  expect(screen.getByLabelText(/Capacity/)).toBeDisabled();
  fireEvent.change(title, { target: { value: 'Updated campus theatre' } });
  fireEvent.submit(title.closest('form'));
  await waitFor(() => expect(request).toHaveBeenCalledWith('/events/7', expect.objectContaining({ method: 'PUT', body: expect.objectContaining({ title: 'Updated campus theatre', capacity: undefined }) })));
  expect(await screen.findByText('Saved event')).toBeInTheDocument();
});
