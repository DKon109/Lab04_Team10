// Agreed explainable "For You" scoring, mirrored by the backend's LikeService:
//   +3 same category as a liked event
//   +2 same organiser as a liked event
//   +1 per matching tag
// Past events are excluded; with no likes yet the upcoming feed is the
// cold-start fallback; popularity (likeCount) only breaks ties when real.

function eq(a, b) {
  return typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
}

export function scoreEvent(candidate, likedEvents) {
  let score = 0;
  for (const liked of likedEvents) {
    if (eq(candidate.category, liked.category)) score += 3;
    if (eq(candidate.organiserName, liked.organiserName)) score += 2;
    for (const tag of candidate.tags || []) {
      if ((liked.tags || []).some((t) => eq(t, tag))) score += 1;
    }
  }
  return score;
}

export function rankForYou(events, likedIds, now = new Date()) {
  const upcoming = events.filter((e) => new Date(e.dateTime) >= now);
  const liked = events.filter((e) => likedIds.has(e.id));

  if (liked.length === 0) {
    return [...upcoming].sort((a, b) => new Date(a.dateTime) - new Date(b.dateTime));
  }

  return upcoming
    .filter((e) => !likedIds.has(e.id))
    .map((e) => ({ event: e, score: scoreEvent(e, liked) }))
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      const aPop = typeof a.event.likeCount === 'number' ? a.event.likeCount : 0;
      const bPop = typeof b.event.likeCount === 'number' ? b.event.likeCount : 0;
      if (bPop !== aPop) return bPop - aPop;
      return new Date(a.event.dateTime) - new Date(b.event.dateTime);
    })
    .map((entry) => entry.event);
}
