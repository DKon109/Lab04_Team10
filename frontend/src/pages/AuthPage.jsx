import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AuthForm } from '../components/auth/AuthForm';

export function AuthPage() {
  const { user, initialising, login, register } = useAuth();
  const [mode, setMode] = useState('login');
  const navigate = useNavigate();

  if (initialising) return null;
  if (user) return <Navigate to="/events" replace />;

  async function handleSubmit(email, password) {
    if (mode === 'signup') {
      // Registration returns no token — auto-login with the same credentials.
      await register(email, password);
    } else {
      await login(email, password);
    }
    navigate('/events', { replace: true });
  }

  return (
    <div className="auth-layout">
      <section className="auth-brand">
        <h1 className="wordmark">
          Tix<span style={{ opacity: 0.65 }}>o</span>ra
        </h1>
        <p className="eyebrow">YOUR CAMPUS. YOUR CROWD.</p>
        <p className="tagline">The best plans<br />start with<br /><em>showing up.</em></p>
        <ul>
          <li>01 — Discover campus and Sydney events</li>
          <li>02 — Bring your friends with private groups</li>
          <li>03 — Keep your bookings in one place</li>
        </ul>
      </section>

      <main className="auth-panel">
        <div className="auth-card">
          <div className="auth-tabs" role="tablist" aria-label="Log in or sign up">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'login'}
              onClick={() => setMode('login')}
            >
              Log in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'signup'}
              onClick={() => setMode('signup')}
            >
              Sign up
            </button>
          </div>
          <AuthForm key={mode} mode={mode} onSubmit={handleSubmit} />
        </div>
      </main>
    </div>
  );
}
