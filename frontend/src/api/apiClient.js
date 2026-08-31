// Central fetch wrapper: base URL, bearer-token attachment, 401 handling and
// safe error-message extraction. All API modules go through request().

const API_BASE = '/api';

const TOKEN_KEY = 'tixora.token';
const EXPIRY_KEY = 'tixora.expiresAt';
const USER_KEY = 'tixora.user';

export class ApiError extends Error {
  constructor(message, status, fieldErrors = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export function storeSession({ token, expiresInSeconds, user }) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(EXPIRY_KEY, String(Date.now() + expiresInSeconds * 1000));
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(EXPIRY_KEY);
  sessionStorage.removeItem(USER_KEY);
}

export function getToken() {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const expiresAt = Number(sessionStorage.getItem(EXPIRY_KEY));
  if (!token || !expiresAt || Date.now() >= expiresAt) {
    return null;
  }
  return token;
}

export function getStoredUser() {
  if (!getToken()) return null;
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}

function notifyUnauthorized() {
  clearSession();
  window.dispatchEvent(new Event('tixora:unauthorized'));
}

// Backend errors arrive either as { message, errors: {field: msg} } from our
// validation handler, or as a standard Spring error/problem body. Read all
// known shapes defensively.
function extractError(data, response) {
  const fieldErrors = data && typeof data.errors === 'object' && data.errors !== null ? data.errors : {};
  const message =
    (data && (data.message || data.detail || data.error)) ||
    `Request failed (${response.status})`;
  return new ApiError(message, response.status, fieldErrors);
}

export async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = auth ? getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError('Cannot reach the server. Is the backend running?', 0);
  }

  let data = null;
  const text = await response.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && auth && token) {
      notifyUnauthorized();
    }
    throw extractError(data, response);
  }
  return data;
}
