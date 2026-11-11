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
          <section className="demo-accounts" aria-labelledby="demo-accounts-heading">
            <p className="eyebrow">EXPLORE THE LIVE DEMO</p>
            <h2 id="demo-accounts-heading">Try either workspace</h2>
            <p>No sign-up needed. Copy a demo email and password into the login form above.</p>
            <div className="demo-account">
              <h3>Organiser</h3>
              <p>Create events, configure venues and manage registration.</p>
              <dl>
                <dt>Email / ID</dt><dd><code>committee.demo@uni.sydney.edu.au</code></dd>
                <dt>Password</dt><dd><code>OrganiserDemo1!</code></dd>
              </dl>
            </div>
            <div className="demo-account">
              <h3>Attendee</h3>
              <p>Discover events, try mock checkout and view your tickets.</p>
              <dl>
                <dt>Email / ID</dt><dd><code>attendee.demo@uni.sydney.edu.au</code></dd>
                <dt>Password</dt><dd><code>TixoraDemo3!</code></dd>
              </dl>
            </div>
            <p className="demo-account-note">Shared demo accounts · use fictional data only. Other visitors can change these records. Payments are simulated; no money is charged.</p>
          </section>
        </div>
      </main>
    </div>
  );
}
