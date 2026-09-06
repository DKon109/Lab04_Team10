import { describe, expect, it } from 'vitest';
import { scoreEvent, rankForYou } from '../services/recommendationService';

const future = (days) => new Date(Date.now() + days * 86400000).toISOString();
const past = (days) => new Date(Date.now() - days * 86400000).toISOString();

function event(id, overrides = {}) {
  return {
    id,
    title: `Event ${id}`,
    dateTime: future(id),
    category: 'Misc',
    organiserName: 'Someone',
    tags: [],
    likedByMe: false,
    likeCount: null,
    ...overrides,
  };
}

describe('recommendation scoring', () => {
  const liked = [
    event(1, { category: 'Technology', organiserName: 'Coding Society', tags: ['coding', 'ai'] }),
  ];

  it('adds +3 for a matching category', () => {
    expect(scoreEvent(event(2, { category: 'Technology' }), liked)).toBe(3);
  });

  it('adds +2 for a matching organiser', () => {
    expect(scoreEvent(event(2, { organiserName: 'Coding Society' }), liked)).toBe(2);
  });

  it('adds +1 per matching tag', () => {
    expect(scoreEvent(event(2, { tags: ['coding', 'ai', 'other'] }), liked)).toBe(2);
  });

  it('combines all signals', () => {
    const candidate = event(2, {
      category: 'Technology',
      organiserName: 'Coding Society',
      tags: ['coding'],
    });
    expect(scoreEvent(candidate, liked)).toBe(6);
  });

  it('matches case-insensitively', () => {
    expect(scoreEvent(event(2, { category: 'TECHNOLOGY' }), liked)).toBe(3);
  });
});

describe('rankForYou', () => {
  it('falls back to upcoming events by date with no likes (cold start)', () => {
    const events = [event(3), event(1), event(2)];
    const ranked = rankForYou(events, new Set());
    expect(ranked.map((e) => e.id)).toEqual([1, 2, 3]);
  });

  it('ranks similar events first and excludes liked and past events', () => {
    const likedEvent = event(1, { category: 'Technology', tags: ['coding'] });
    const similar = event(2, { category: 'Technology' });
    const unrelated = event(3, { category: 'Music' });
    const pastSimilar = event(4, { category: 'Technology', dateTime: past(2) });

    const ranked = rankForYou([likedEvent, unrelated, similar, pastSimilar], new Set([1]));
    expect(ranked.map((e) => e.id)).toEqual([2, 3]);
  });

  it('uses real like counts only as a tie-break', () => {
    const likedEvent = event(1, { category: 'Technology' });
    const a = event(2, { category: 'Technology', likeCount: null });
    const b = event(3, { category: 'Technology', likeCount: 5 });

    const ranked = rankForYou([likedEvent, a, b], new Set([1]));
    expect(ranked.map((e) => e.id)).toEqual([3, 2]);
  });
});
