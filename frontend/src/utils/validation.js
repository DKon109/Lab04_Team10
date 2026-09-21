// Client-side rules mirror the backend's RegistrationRequest constraints.

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const USYD_DOMAIN = '@uni.sydney.edu.au';

export function validateEmail(email) {
  if (!email || !email.trim()) return 'Email is required';
  if (!EMAIL_PATTERN.test(email.trim())) return 'Email must be valid';
  return '';
}

export function validateUsydEmail(email) {
  const basic = validateEmail(email);
  if (basic) return basic;
  if (!email.trim().toLowerCase().endsWith(USYD_DOMAIN)) {
    return `Registration currently requires a ${USYD_DOMAIN.slice(1)} email`;
  }
  return '';
}

export function validatePassword(password) {
  if (!password) return 'Password is required';
  if (password.length < 8 || password.length > 72) {
    return 'Password must be between 8 and 72 characters';
  }
  return '';
}

export function validateConfirmPassword(password, confirm) {
  if (!confirm) return 'Please confirm your password';
  if (password !== confirm) return 'Passwords do not match';
  return '';
}
