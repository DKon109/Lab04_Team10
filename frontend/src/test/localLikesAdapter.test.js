import { describe, expect, it } from 'vitest';
import { createLocalLikesAdapter } from '../services/localLikesAdapter';

describe('local likes adapter', () => {
  it('starts with no likes', async () => {
    const adapter = createLocalLikesAdapter(1);
    expect(await adapter.getLikedIds()).toEqual([]);
  });

  it('like/unlike toggles and persists to localStorage', async () => {
    const adapter = createLocalLikesAdapter(1);
    await adapter.like(42);
    expect(await adapter.getLikedIds()).toEqual([42]);

    // A fresh adapter (simulating a page refresh) still sees the like.
    const reloaded = createLocalLikesAdapter(1);
    expect(await reloaded.getLikedIds()).toEqual([42]);

    await reloaded.unlike(42);
    expect(await reloaded.getLikedIds()).toEqual([]);
  });

  it('liking twice does not duplicate', async () => {
    const adapter = createLocalLikesAdapter(1);
    await adapter.like(42);
    await adapter.like(42);
    expect(await adapter.getLikedIds()).toEqual([42]);
  });

  it('keeps likes separate per user', async () => {
    const a = createLocalLikesAdapter(1);
    const b = createLocalLikesAdapter(2);
    await a.like(42);
    expect(await b.getLikedIds()).toEqual([]);
  });

  it('For You reflects liked events', async () => {
    const adapter = createLocalLikesAdapter(1);
    await adapter.like(1);
    const future = (days) => new Date(Date.now() + days * 86400000).toISOString();
    const events = [
      { id: 1, category: 'Technology', organiserName: 'A', tags: [], dateTime: future(1) },
      { id: 2, category: 'Music', organiserName: 'B', tags: [], dateTime: future(2) },
      { id: 3, category: 'Technology', organiserName: 'C', tags: [], dateTime: future(3) },
    ];
    const forYou = await adapter.getForYou(async () => events);
    expect(forYou.map((e) => e.id)).toEqual([3, 2]);
  });
});
