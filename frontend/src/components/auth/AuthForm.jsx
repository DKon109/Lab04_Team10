import { useState } from 'react';
import {
  validateEmail,
  validateUsydEmail,
  validatePassword,
  validateConfirmPassword,
} from '../../utils/validation';

function PasswordField({ id, label, value, onChange, error, autoComplete }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="password-wrap">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={onChange}
          autoComplete={autoComplete}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
        />
        <button
          type="button"
          className="toggle-visibility"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
        >
          {visible ? 'Hide' : 'Show'}
        </button>
      </div>
      {error && (
        <span className="field-error" id={`${id}-error`}>
          {error}
        </span>
      )}
    </div>
  );
}

/**
 * Login / Sign Up form with client-side validation matching the backend
 * rules. onSubmit(email, password) is provided by AuthPage; server errors
 * come back through the thrown ApiError's message/fieldErrors.
 */
export function AuthForm({ mode, onSubmit }) {
  const isSignup = mode === 'signup';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    const nextErrors = {
      email: isSignup ? validateUsydEmail(email) : validateEmail(email),
      password: validatePassword(password),
      confirm: isSignup ? validateConfirmPassword(password, confirm) : '',
    };
    setErrors(nextErrors);
    setServerError('');
    if (Object.values(nextErrors).some(Boolean)) return;

    setSubmitting(true);
    try {
      await onSubmit(email.trim(), password);
    } catch (error) {
      const fieldErrors = error.fieldErrors || {};
      setErrors((prev) => ({
        ...prev,
        email: fieldErrors.email || '',
        password: fieldErrors.password || '',
      }));
      if (!fieldErrors.email && !fieldErrors.password) {
        setServerError(error.message || 'Something went wrong. Please try again.');
      }
      setSubmitting(false);
    }
    // On success the page navigates away; no need to reset submitting.
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      {serverError && (
        <div className="form-banner error" role="alert">
          {serverError}
        </div>
      )}

      {isSignup && (
        <p className="auth-note">
          Registration currently requires a <strong>@uni.sydney.edu.au</strong> email.
        </p>
      )}

      <div className="field">
        <label htmlFor="auth-email">Email</label>
        <input
          id="auth-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'auth-email-error' : undefined}
        />
        {errors.email && (
          <span className="field-error" id="auth-email-error">
            {errors.email}
          </span>
        )}
      </div>

      <PasswordField
        id="auth-password"
        label="Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.password}
        autoComplete={isSignup ? 'new-password' : 'current-password'}
      />

      {isSignup && (
        <PasswordField
          id="auth-confirm"
          label="Confirm password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={errors.confirm}
          autoComplete="new-password"
        />
      )}

      <button type="submit" className="btn btn-primary" disabled={submitting} style={{ width: '100%' }}>
        {submitting ? 'Please wait…' : isSignup ? 'Create account' : 'Log in'}
      </button>
    </form>
  );
}
