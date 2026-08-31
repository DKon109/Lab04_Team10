import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../components/common/ProtectedRoute';

// AuthContext re-validates a restored session against /api/auth/me on mount;
// echo back the stored user instead of hitting the network.
vi.mock('../api/authApi', () => ({
  me: vi.fn(() =>
    Promise.resolve(JSON.parse(sessionStorage.getItem('tixora.user')))
  ),
  login: vi.fn(),
  register: vi.fn(),
}));

function renderAt(path) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/auth" element={<div>Auth page</div>} />
          <Route
            path="/events"
            element={
              <ProtectedRoute>
                <div>Events page</div>
              </ProtectedRoute>
            }
          />
          <Route
            path="/organiser"
            element={
              <ProtectedRoute role="COMMITTEE_MEMBER">
                <div>Organiser page</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

function seedSession(role = 'STUDENT') {
  sessionStorage.setItem('tixora.token', 'test-token');
  sessionStorage.setItem('tixora.expiresAt', String(Date.now() + 3600_000));
  sessionStorage.setItem(
    'tixora.user',
    JSON.stringify({ id: 1, email: 'student@uni.sydney.edu.au', role })
  );
}

describe('protected routing', () => {
  it('redirects unauthenticated visitors to the auth page', () => {
    renderAt('/events');
    expect(screen.getByText('Auth page')).toBeInTheDocument();
  });

  it('renders the protected page for an authenticated user', async () => {
    seedSession();
    renderAt('/events');
    expect(await screen.findByText('Events page')).toBeInTheDocument();
  });

  it('sends non-organisers back to the events feed', async () => {
    seedSession('STUDENT');
    renderAt('/organiser');
    expect(await screen.findByText('Events page')).toBeInTheDocument();
    expect(screen.queryByText('Organiser page')).not.toBeInTheDocument();
  });

  it('lets organisers into the organiser dashboard', async () => {
    seedSession('COMMITTEE_MEMBER');
    renderAt('/organiser');
    expect(await screen.findByText('Organiser page')).toBeInTheDocument();
  });
});
