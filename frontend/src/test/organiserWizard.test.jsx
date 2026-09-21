import { describe, it, expect } from 'vitest';
import { validateStep } from '../pages/EventCreationWizard';

const VALID_DRAFT = {
  title: 'Quad Night Market',
  category: 'Food',
  dateTime: '2026-10-15T18:00',
  venue: 'The Quadrangle',
  city: 'Sydney',
  audience: 'USYD',
  organiserName: 'USYD Food Society',
  organiserType: 'SOCIETY',
  imageUrl: '',
  description: 'A night market.',
  tags: 'food, market',
  venueLayout: {
    mode: 'usyd',
    roomId: 'woolley',
    capacity: 350,
    hasNumberedSeats: true,
  },
  seatingMode: 'general-admission',
  unnumberedConfirmed: false,
};

describe('validateStep — step 1 (basics)', () => {
  it('returns no errors for a valid draft', () => {
    expect(validateStep(1, VALID_DRAFT)).toEqual({});
  });

  it('requires a title', () => {
    const err = validateStep(1, { ...VALID_DRAFT, title: '' });
    expect(err.title).toBeTruthy();
  });

  it('requires a category', () => {
    const err = validateStep(1, { ...VALID_DRAFT, category: '' });
    expect(err.category).toBeTruthy();
  });
});

describe('validateStep — step 2 (schedule + venue)', () => {
  it('returns no errors for a valid draft', () => {
    expect(validateStep(2, VALID_DRAFT)).toEqual({});
  });

  it('requires a date and time', () => {
    const err = validateStep(2, { ...VALID_DRAFT, dateTime: '' });
    expect(err.dateTime).toBeTruthy();
  });

  it('requires a venue name', () => {
    const err = validateStep(2, { ...VALID_DRAFT, venue: '' });
    expect(err.venue).toBeTruthy();
  });
});

describe('validateStep — step 3 (venue layout + seating)', () => {
  it('requires a venue layout', () => {
    const err = validateStep(3, { ...VALID_DRAFT, venueLayout: null });
    expect(err.venueLayout).toBeTruthy();
  });

  it('allows group-allocated on a numbered venue without confirmation', () => {
    const err = validateStep(3, {
      ...VALID_DRAFT,
      venueLayout: { ...VALID_DRAFT.venueLayout, hasNumberedSeats: true },
      seatingMode: 'group-allocated',
      unnumberedConfirmed: false,
    });
    expect(err.venueLayout).toBeUndefined();
  });

  it('blocks group-allocated on an unnumbered venue without confirmation', () => {
    const err = validateStep(3, {
      ...VALID_DRAFT,
      venueLayout: { ...VALID_DRAFT.venueLayout, hasNumberedSeats: false },
      seatingMode: 'group-allocated',
      unnumberedConfirmed: false,
    });
    expect(err.venueLayout).toBeTruthy();
  });

  it('allows group-allocated on an unnumbered venue once confirmed', () => {
    const err = validateStep(3, {
      ...VALID_DRAFT,
      venueLayout: { ...VALID_DRAFT.venueLayout, hasNumberedSeats: false },
      seatingMode: 'group-allocated',
      unnumberedConfirmed: true,
    });
    expect(err.venueLayout).toBeUndefined();
  });

  it('allows general-admission on an unnumbered venue without confirmation', () => {
    const err = validateStep(3, {
      ...VALID_DRAFT,
      venueLayout: { ...VALID_DRAFT.venueLayout, hasNumberedSeats: false },
      seatingMode: 'general-admission',
      unnumberedConfirmed: false,
    });
    expect(err.venueLayout).toBeUndefined();
  });
});

describe('validateStep — step 4 (organiser details)', () => {
  it('requires an organiser name', () => {
    const err = validateStep(4, { ...VALID_DRAFT, organiserName: '' });
    expect(err.organiserName).toBeTruthy();
  });

  it('rejects a malformed image URL', () => {
    const err = validateStep(4, { ...VALID_DRAFT, imageUrl: 'not-a-url' });
    expect(err.imageUrl).toBeTruthy();
  });

  it('accepts a valid https image URL', () => {
    const err = validateStep(4, {
      ...VALID_DRAFT,
      imageUrl: 'https://example.com/x.jpg',
    });
    expect(err.imageUrl).toBeUndefined();
  });

  it('accepts an empty image URL', () => {
    expect(validateStep(4, VALID_DRAFT)).toEqual({});
  });
});

describe('validateStep — step 5 (review)', () => {
  it('has no validation on the review step', () => {
    expect(validateStep(5, VALID_DRAFT)).toEqual({});
  });
});