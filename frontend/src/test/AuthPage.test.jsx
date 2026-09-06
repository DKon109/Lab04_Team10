import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { AuthPage } from '../pages/AuthPage';
import * as authApi from '../api/authApi';

vi.mock('../api/authApi', () => ({
  register: vi.fn(),
  login: vi.fn(),
  me: vi.fn(),
}));

function renderAuthPage() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/auth']}>
        <Routes>
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/events" element={<div>Events feed</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

const EMAIL = 'student@uni.sydney.edu.au';
const PASSWORD = 'password123';

describe('AuthPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('rejects a non-USYD email on sign up without calling the API', async () => {
    const user = userEvent.setup();
    renderAuthPage();

    await user.click(screen.getByRole('tab', { name: 'Sign up' }));
    await user.type(screen.getByLabelText('Email'), 'student@gmail.com');
    await user.type(screen.getByLabelText('Password'), PASSWORD);
    await user.type(screen.getByLabelText('Confirm password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(
      await screen.findByText('Registration currently requires a uni.sydney.edu.au email')
    ).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('shows inline errors for a short password', async () => {
    const user = userEvent.setup();
    renderAuthPage();

    await user.type(screen.getByLabelText('Email'), EMAIL);
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText(/between 8 and 72/)).toBeInTheDocument();
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('automatically logs in after successful registration and redirects to the feed', async () => {
    authApi.register.mockResolvedValue({ id: 1, email: EMAIL, role: 'STUDENT' });
    authApi.login.mockResolvedValue({
      id: 1,
      email: EMAIL,
      role: 'STUDENT',
      token: 'fresh-token',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
    });

    const user = userEvent.setup();
    renderAuthPage();

    await user.click(screen.getByRole('tab', { name: 'Sign up' }));
    await user.type(screen.getByLabelText('Email'), EMAIL);
    await user.type(screen.getByLabelText('Password'), PASSWORD);
    await user.type(screen.getByLabelText('Confirm password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Events feed')).toBeInTheDocument();
    expect(authApi.register).toHaveBeenCalledWith(EMAIL, PASSWORD);
    expect(authApi.login).toHaveBeenCalledWith(EMAIL, PASSWORD);
    expect(sessionStorage.getItem('tixora.token')).toBe('fresh-token');
  });

  it('surfaces a server error from login', async () => {
    const error = new Error('Invalid email or password');
    authApi.login.mockRejectedValue(error);

    const user = userEvent.setup();
    renderAuthPage();

    await user.type(screen.getByLabelText('Email'), EMAIL);
    await user.type(screen.getByLabelText('Password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });
});
