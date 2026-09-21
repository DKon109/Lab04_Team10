// Client-side search across title, organiser, venue, category and tags.
// If nothing matches the full query, falls back to matching any word of it
// ("theme match": a search for "coding party" still surfaces coding events).

function haystack(event) {
  return [
    event.title,
    event.organiserName,
    event.venue,
    event.category,
    ...(event.tags || []),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

export function searchEvents(events, query) {
  const q = (query || '').trim().toLowerCase();
  if (!q) return { results: events, fallback: false };

  const exact = events.filter((e) => haystack(e).includes(q));
  if (exact.length > 0) return { results: exact, fallback: false };

  const words = q.split(/\s+/).filter(Boolean);
  const loose = events
    .map((e) => ({
      event: e,
      hits: words.filter((w) => haystack(e).includes(w)).length,
    }))
    .filter((entry) => entry.hits > 0)
    .sort((a, b) => b.hits - a.hits)
    .map((entry) => entry.event);

  return { results: loose, fallback: loose.length > 0 };
}
