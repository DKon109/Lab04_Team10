import { describe, expect, it } from 'vitest';
import {
  validateEmail,
  validateUsydEmail,
  validatePassword,
  validateConfirmPassword,
} from '../utils/validation';

describe('auth form validation', () => {
  it('requires an email', () => {
    expect(validateEmail('')).toBe('Email is required');
    expect(validateEmail('  ')).toBe('Email is required');
  });

  it('rejects malformed emails', () => {
    expect(validateEmail('not-an-email')).toBe('Email must be valid');
    expect(validateEmail('a@b')).toBe('Email must be valid');
  });

  it('accepts a valid email', () => {
    expect(validateEmail('student@uni.sydney.edu.au')).toBe('');
  });

  it('registration requires the USYD domain', () => {
    expect(validateUsydEmail('student@gmail.com')).toMatch(/uni\.sydney\.edu\.au/);
    expect(validateUsydEmail('student@uni.sydney.edu.au')).toBe('');
    // domain check is case-insensitive
    expect(validateUsydEmail('Student@UNI.SYDNEY.EDU.AU')).toBe('');
  });

  it('enforces the backend password length rules (8–72)', () => {
    expect(validatePassword('')).toBe('Password is required');
    expect(validatePassword('short')).toMatch(/between 8 and 72/);
    expect(validatePassword('x'.repeat(73))).toMatch(/between 8 and 72/);
    expect(validatePassword('password123')).toBe('');
  });

  it('requires matching password confirmation', () => {
    expect(validateConfirmPassword('password123', '')).toBe('Please confirm your password');
    expect(validateConfirmPassword('password123', 'password124')).toBe('Passwords do not match');
    expect(validateConfirmPassword('password123', 'password123')).toBe('');
  });
});
