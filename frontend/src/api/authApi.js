import { request } from './apiClient';

export function register(email, password) {
  return request('/auth/register', { method: 'POST', body: { email, password }, auth: false });
}

export function login(email, password) {
  return request('/auth/login', { method: 'POST', body: { email, password }, auth: false });
}

export function me() {
  return request('/auth/me');
}
