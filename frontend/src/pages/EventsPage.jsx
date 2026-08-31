import { useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useLikes } from '../hooks/useLikes';
import { useEvents } from '../hooks/useEvents';
import { TopNav } from '../components/layout/TopNav';
import { FilterBar } from '../components/events/FilterBar';
import { EventCard } from '../components/events/EventCard';
import { EventDetailsModal } from '../components/events/EventDetailsModal';
import { SkeletonGrid, ErrorState, EmptyState } from '../components/common/FeedStates';
import { searchEvents } from '../utils/searchEvents';
import { getLikesMode } from '../services/likesService';

const TAB_SUBTITLES = {
  ALL: "What's popping across Sydney",
  USYD: 'On campus — societies, faculties and everything between',
  FORYOU: 'Picked for you, based on the events you like',
};

export function EventsPage() {
  const { user } = useAuth();
  const { adapter, likedIds, countDelta, toggleLike, likesVersion } = useLikes(user);

  const [tab, setTab] = useState('ALL');
  const [organiserType, setOrganiserType] = useState('');
  const [category, setCategory] = useState('');
  const [date, setDate] = useState('');
  const [search, setSearch] = useState('');
  const [selectedEvent, setSelectedEvent] = useState(null);

  const { events, loading, error, retry } = useEvents(tab, organiserType, {
    adapter,
    likesVersion,
  });

  function changeTab(nextTab) {
    setTab(nextTab);
    setCategory('');
    setDate('');
  }

  const categories = useMemo(
    () => [...new Set(events.map((e) => e.category).filter(Boolean))].sort(),
    [events]
  );

  const { results: visibleEvents, fallback: searchFallback } = useMemo(() => {
    let filtered = events;
    if (category) filtered = filtered.filter((e) => e.category === category);
    if (date) filtered = filtered.filter((e) => e.dateTime?.startsWith(date));
    return searchEvents(filtered, search);
  }, [events, category, date, search]);

  const hasActiveFilters = Boolean(category || date || search.trim());

  function displayCount(event) {
    if (typeof event.likeCount !== 'number') return null;
    return event.likeCount + (countDelta[event.id] || 0);
  }

  function renderFeed() {
    if (loading) return <SkeletonGrid />;
    if (error) return <ErrorState message={error.message} onRetry={retry} />;

    if (events.length === 0 && tab === 'FORYOU') {
      return (
        <EmptyState title="Nothing to recommend yet">
          <p>Like a few events in All Sydney or USYD and your feed will learn what you're into.</p>
        </EmptyState>
      );
    }
    if (visibleEvents.length === 0) {
      return (
        <EmptyState title={hasActiveFilters ? 'No events match those filters' : 'No events yet'}>
          {hasActiveFilters ? (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setCategory('');
                setDate('');
                setSearch('');
              }}
            >
              Clear filters
            </button>
          ) : (
            <p>Check back soon — new events land here all the time.</p>
          )}
        </EmptyState>
      );
    }

    return (
      <>
        {searchFallback && (
          <p className="results-note" role="status">
            No exact matches for “{search.trim()}” — showing the closest events instead.
          </p>
        )}
        <div className="event-grid">
          {visibleEvents.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              liked={likedIds.has(event.id)}
              likeCount={displayCount(event)}
              onToggleLike={(e) => toggleLike(e).catch(() => {})}
              onOpenDetails={setSelectedEvent}
            />
          ))}
        </div>
      </>
    );
  }

  return (
    <>
      <TopNav activeTab={tab} onTabChange={changeTab} />
      <main className="page">
        <h1 className="page-heading">Find your next thing</h1>
        <p className="page-sub">
          {TAB_SUBTITLES[tab]}
          {tab === 'FORYOU' && getLikesMode() === 'local' && ' (local demo mode)'}
        </p>

        <FilterBar
          tab={tab}
          search={search}
          onSearchChange={setSearch}
          organiserType={organiserType}
          onOrganiserTypeChange={setOrganiserType}
          category={category}
          onCategoryChange={setCategory}
          categories={categories}
          date={date}
          onDateChange={setDate}
        />

        {renderFeed()}
      </main>

      {selectedEvent && (
        <EventDetailsModal
          event={selectedEvent}
          liked={likedIds.has(selectedEvent.id)}
          likeCount={displayCount(selectedEvent)}
          onToggleLike={(e) => toggleLike(e).catch(() => {})}
          onClose={() => setSelectedEvent(null)}
        />
      )}
    </>
  );
}
