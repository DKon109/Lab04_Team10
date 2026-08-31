import { describe, expect, it } from 'vitest';
import { buildEventsQuery } from '../api/eventsApi';

describe('event query generation', () => {
  it('All Sydney sends no parameters', () => {
    expect(buildEventsQuery({ scope: 'ALL' })).toBe('');
    expect(buildEventsQuery({})).toBe('');
  });

  it('USYD sends scope=USYD', () => {
    expect(buildEventsQuery({ scope: 'USYD' })).toBe('?scope=USYD');
  });

  it('society filter adds organiserType=SOCIETY', () => {
    expect(buildEventsQuery({ scope: 'USYD', organiserType: 'SOCIETY' })).toBe(
      '?scope=USYD&organiserType=SOCIETY'
    );
  });

  it('faculty filter adds organiserType=FACULTY', () => {
    expect(buildEventsQuery({ scope: 'USYD', organiserType: 'FACULTY' })).toBe(
      '?scope=USYD&organiserType=FACULTY'
    );
  });

  it('empty organiser filter is omitted', () => {
    expect(buildEventsQuery({ scope: 'USYD', organiserType: '' })).toBe('?scope=USYD');
  });

  it('supports category and date parameters', () => {
    expect(
      buildEventsQuery({ scope: 'USYD', category: 'Careers', date: '2026-09-12' })
    ).toBe('?scope=USYD&category=Careers&date=2026-09-12');
  });
});
