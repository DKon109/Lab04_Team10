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
        <p className="tagline">Your campus. Your crowd. Your next event.</p>
        <ul>
          <li>🎟️ Every USYD society and faculty event in one feed</li>
          <li>🌏 The best of what's on across Sydney</li>
          <li>💛 Like events and get a feed that learns your taste</li>
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
