const ORGANISER_OPTIONS = [
  { id: '', label: 'All' },
  { id: 'SOCIETY', label: 'Societies' },
  { id: 'FACULTY', label: 'Faculty' },
];

/**
 * Search + filters. The organiser segment only appears on the USYD tab.
 * Category options are derived from the events actually in the feed.
 */
export function FilterBar({
  tab,
  search,
  onSearchChange,
  organiserType,
  onOrganiserTypeChange,
  category,
  onCategoryChange,
  categories,
  date,
  onDateChange,
}) {
  return (
    <div className="filter-bar">
      <div className="field search-field">
        <label htmlFor="event-search">Search events</label>
        <input
          id="event-search"
          type="search"
          placeholder="Try “hackathon”, “Manning Bar”, “jazz”…"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>

      {tab === 'USYD' && (
        <div className="field">
          <span id="organiser-filter-label" style={{ fontSize: '0.875rem', fontWeight: 600 }}>
            Organised by
          </span>
          <div className="segmented" role="group" aria-labelledby="organiser-filter-label">
            {ORGANISER_OPTIONS.map((option) => (
              <button
                key={option.id || 'ALL'}
                type="button"
                aria-pressed={organiserType === option.id}
                onClick={() => onOrganiserTypeChange(option.id)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="field">
        <label htmlFor="category-filter">Category</label>
        <select
          id="category-filter"
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
        >
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="date-filter">Date</label>
        <input
          id="date-filter"
          type="date"
          value={date}
          onChange={(e) => onDateChange(e.target.value)}
        />
      </div>
    </div>
  );
}
