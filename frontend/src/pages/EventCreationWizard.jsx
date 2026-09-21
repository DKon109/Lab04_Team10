import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { createEvent } from '../api/eventsApi';
import { formatEventDate } from '../utils/format';
import { VenuePicker } from '../components/VenuePicker';
import './EventCreationWizard.css';

const TOTAL_STEPS = 5;

const CATEGORY_SUGGESTIONS = [
  'Music', 'Careers', 'Sport', 'Arts & Culture',
  'Food & Social', 'Workshop', 'Party', 'Technology',
];

const EMPTY_DRAFT = {
  title: '',
  description: '',
  category: '',
  dateTime: '',
  venue: '',
  city: 'Sydney',
  audience: 'USYD',
  organiserName: '',
  organiserType: 'SOCIETY',
  imageUrl: '',
  tags: '',
  venueLayout: null,
  seatingMode: 'general-admission',
  unnumberedConfirmed: false,
};

// Exported for unit tests — pure function, no React.
export function validateStep(step, draft) {
  const errors = {};

  if (step === 1) {
    if (!draft.title.trim()) errors.title = 'Give the event a title.';
    if (!draft.category.trim()) errors.category = 'Pick or type a category.';
  }

  if (step === 2) {
    if (!draft.dateTime) errors.dateTime = 'Pick a date and time.';
    if (!draft.venue.trim()) errors.venue = 'Where is it happening?';
  }

  if (step === 3) {
    if (!draft.venueLayout) errors.venueLayout = 'Pick a room or configure a template.';

    // Physical execution rule from the proposal: group-allocated
    // seating on an unnumbered venue requires explicit confirmation.
    const unnumbered = draft.venueLayout?.hasNumberedSeats === false;
    if (
      unnumbered &&
      draft.seatingMode === 'group-allocated' &&
      !draft.unnumberedConfirmed
    ) {
      errors.venueLayout =
        'Confirm that Tixora seat identifiers will be physically installed, or switch to general admission.';
    }
  }

  if (step === 4) {
    if (!draft.organiserName.trim()) errors.organiserName = 'Who is running this?';
    if (draft.imageUrl && !/^https?:\/\//i.test(draft.imageUrl)) {
      errors.imageUrl = 'Image URL must start with http:// or https://';
    }
  }

  return errors;
}

export function EventCreationWizard() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [draft, setDraft] = useState(EMPTY_DRAFT);
  const [errors, setErrors] = useState({});
  const [banner, setBanner] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (e) => setDraft((prev) => ({ ...prev, [field]: e.target.value }));
  }

  function setLayout(layout) {
    setDraft((prev) => ({
      ...prev,
      venueLayout: layout,
      unnumberedConfirmed: false,
    }));
  }

  function goBack() {
    setErrors({});
    setStep((s) => Math.max(1, s - 1));
  }

  async function goNext() {
    const found = validateStep(step, draft);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setErrors({});

    if (step < TOTAL_STEPS) {
      setStep((s) => s + 1);
      return;
    }

    setSubmitting(true);
    try {
      const created = await createEvent({
        title: draft.title.trim(),
        category: draft.category.trim(),
        dateTime: draft.dateTime,
        venue: draft.venue.trim(),
        city: draft.city,
        university: draft.audience === 'USYD' ? 'USYD' : null,
        organiserName: draft.organiserName.trim(),
        organiserType: draft.organiserType,
        imageUrl: draft.imageUrl.trim() || null,
        description: draft.description.trim() || null,
        tags: draft.tags
          .split(',')
          .map((t) => t.trim().toLowerCase())
          .filter(Boolean),
        seatingMode: draft.seatingMode,
        venueLayout: draft.venueLayout,
      });
      navigate(`/events/${created.id}`, {
        state: { justPublished: true },
      });
    } catch (error) {
      setErrors(error.fieldErrors || {});
      setBanner({
        type: 'error',
        text: error.message || 'Could not publish the event. Please try again.',
      });
    } finally {
      setSubmitting(false);
    }
  }

  const fieldError = (name) =>
    errors[name] ? <span className="field-error">{errors[name]}</span> : null;

  return (
    <>
      <TopNav />
      <main className="page">
        <h1 className="page-heading">Create event</h1>
        <p className="page-sub">
          Step {step} of {TOTAL_STEPS}
        </p>

        <div className="step-track" aria-hidden="true">
          {Array.from({ length: TOTAL_STEPS }).map((_, i) => {
            const n = i + 1;
            const state = n < step ? 'done' : n === step ? 'current' : '';
            return <span key={n} className={`step-dot ${state}`} />;
          })}
        </div>

        {banner && (
          <div className={`form-banner ${banner.type}`} role="alert">
            {banner.text}
          </div>
        )}

        <section className="panel">
          {step === 1 && (
            <>
              <div className="field">
                <label htmlFor="w-title">Title</label>
                <input id="w-title" value={draft.title} onChange={update('title')} />
                {fieldError('title')}
              </div>
              <div className="field">
                <label htmlFor="w-description">Description</label>
                <textarea
                  id="w-description"
                  rows={4}
                  value={draft.description}
                  onChange={update('description')}
                  placeholder="What should people know before they come?"
                />
                {fieldError('description')}
              </div>
              <div className="field">
                <label htmlFor="w-category">Category</label>
                <input
                  id="w-category"
                  list="w-category-options"
                  value={draft.category}
                  onChange={update('category')}
                  placeholder="e.g. Music, Careers, Workshop"
                />
                <datalist id="w-category-options">
                  {CATEGORY_SUGGESTIONS.map((c) => (
                    <option key={c} value={c} />
                  ))}
                </datalist>
                {fieldError('category')}
              </div>
            </>
          )}

          {step === 2 && (
            <>
              <div className="field">
                <label htmlFor="w-datetime">Date and time</label>
                <input
                  id="w-datetime"
                  type="datetime-local"
                  value={draft.dateTime}
                  onChange={update('dateTime')}
                />
                {fieldError('dateTime')}
              </div>
              <div className="form-row">
                <div className="field">
                  <label htmlFor="w-venue">Venue name</label>
                  <input id="w-venue" value={draft.venue} onChange={update('venue')} />
                  {fieldError('venue')}
                </div>
                <div className="field">
                  <label htmlFor="w-city">City</label>
                  <input id="w-city" value={draft.city} onChange={update('city')} />
                </div>
              </div>
            </>
          )}

          {step === 3 && (
            <>
              <p className="page-sub" style={{ marginTop: 0 }}>
                Choose a USYD room, or configure an external layout template.
              </p>

              <VenuePicker value={draft.venueLayout} onSelect={setLayout} />

              <fieldset className="field" style={{ marginTop: 16, border: 0, padding: 0 }}>
                <legend
                  style={{
                    fontSize: '0.875rem',
                    fontWeight: 600,
                    marginBottom: 8,
                  }}
                >
                  Seating mode
                </legend>
                <label style={{ display: 'block', marginBottom: 6 }}>
                  <input
                    type="radio"
                    name="seatingMode"
                    value="general-admission"
                    checked={draft.seatingMode === 'general-admission'}
                    onChange={(e) =>
                      setDraft((prev) => ({
                        ...prev,
                        seatingMode: e.target.value,
                      }))
                    }
                  />{' '}
                  General admission
                </label>
                <label style={{ display: 'block' }}>
                  <input
                    type="radio"
                    name="seatingMode"
                    value="group-allocated"
                    checked={draft.seatingMode === 'group-allocated'}
                    onChange={(e) =>
                      setDraft((prev) => ({
                        ...prev,
                        seatingMode: e.target.value,
                      }))
                    }
                  />{' '}
                  Group allocated
                </label>
              </fieldset>

              {draft.venueLayout?.hasNumberedSeats === false &&
                draft.seatingMode === 'group-allocated' && (
                  <label
                    className="venue-checkbox"
                    style={{ marginTop: 12, display: 'flex', gap: 8 }}
                  >
                    <input
                      type="checkbox"
                      checked={draft.unnumberedConfirmed}
                      onChange={(e) =>
                        setDraft((prev) => ({
                          ...prev,
                          unnumberedConfirmed: e.target.checked,
                        }))
                      }
                    />
                    I confirm Tixora-generated row and seat identifiers will
                    be physically installed at this venue.
                  </label>
                )}

              {fieldError('venueLayout')}
            </>
          )}

          {step === 4 && (
            <>
              <div className="form-row">
                <div className="field">
                  <label htmlFor="w-audience">Audience</label>
                  <select id="w-audience" value={draft.audience} onChange={update('audience')}>
                    <option value="USYD">USYD campus</option>
                    <option value="SYDNEY">All Sydney</option>
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="w-organiser-type">Organiser type</label>
                  <select
                    id="w-organiser-type"
                    value={draft.organiserType}
                    onChange={update('organiserType')}
                  >
                    <option value="SOCIETY">Society</option>
                    <option value="FACULTY">Faculty</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
              </div>
              <div className="field">
                <label htmlFor="w-organiser-name">Organiser name</label>
                <input
                  id="w-organiser-name"
                  value={draft.organiserName}
                  onChange={update('organiserName')}
                />
                {fieldError('organiserName')}
              </div>
              <div className="field">
                <label htmlFor="w-image">Image URL (optional)</label>
                <input
                  id="w-image"
                  type="url"
                  value={draft.imageUrl}
                  onChange={update('imageUrl')}
                  placeholder="https://…"
                />
                {fieldError('imageUrl')}
              </div>
              <div className="field">
                <label htmlFor="w-tags">Tags (comma separated)</label>
                <input
                  id="w-tags"
                  value={draft.tags}
                  onChange={update('tags')}
                  placeholder="networking, free-food"
                />
              </div>
            </>
          )}

          {step === 5 && (
            <>
              <p className="page-sub" style={{ marginTop: 0 }}>
                Here's how your event will appear in the feed.
              </p>

              <div className="wizard-preview">
                <DraftPreviewCard draft={draft} />
              </div>

              <h2 style={{ fontSize: '1.05rem', margin: '24px 0 8px' }}>
                Full details
              </h2>
              <dl className="detail-list">
                <dt>Date &amp; time</dt>
                <dd>{draft.dateTime ? formatEventDate(draft.dateTime) : '—'}</dd>
                <dt>Location</dt>
                <dd>{draft.venue ? `${draft.venue}, ${draft.city}` : '—'}</dd>
                <dt>Venue layout</dt>
                <dd>
                  {draft.venueLayout
                    ? `${draft.venueLayout.mode === 'usyd' ? 'USYD room' : 'External template'} · capacity ${draft.venueLayout.capacity}`
                    : '—'}
                </dd>
                <dt>Seating mode</dt>
                <dd>
                  {draft.seatingMode === 'group-allocated'
                    ? 'Group allocated'
                    : 'General admission'}
                </dd>
                <dt>Audience</dt>
                <dd>{draft.audience === 'USYD' ? 'USYD campus' : 'All Sydney'}</dd>
                <dt>Organiser</dt>
                <dd>
                  {draft.organiserName || '—'} ({draft.organiserType})
                </dd>
                <dt>Tags</dt>
                <dd>{draft.tags || '—'}</dd>
                <dt>Description</dt>
                <dd>{draft.description || '—'}</dd>
              </dl>
            </>
          )}

          <div className="wizard-actions">
            {step > 1 && (
              <button
                type="button"
                className="btn btn-ghost"
                onClick={goBack}
                disabled={submitting}
              >
                Back
              </button>
            )}
            <button
              type="button"
              className="btn btn-primary"
              onClick={goNext}
              disabled={submitting}
            >
              {submitting
                ? 'Publishing…'
                : step === TOTAL_STEPS
                ? 'Publish event'
                : 'Next'}
            </button>
          </div>
        </section>
      </main>
    </>
  );
}

function DraftPreviewCard({ draft }) {
  const tags = draft.tags
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);

  const validDate =
    draft.dateTime && !Number.isNaN(new Date(draft.dateTime).getTime());

  return (
    <article className="event-card wizard-preview-card">
      <div className="media">
        {draft.imageUrl ? (
          <img src={draft.imageUrl} alt="" />
        ) : (
          <div className="image-fallback">
            {(draft.title || '?').charAt(0).toUpperCase()}
          </div>
        )}
        {draft.category && (
          <span className="badge badge-category">{draft.category}</span>
        )}
      </div>
      <div className="body">
        <p className="when">
          {validDate ? formatEventDate(draft.dateTime) : 'Date not set yet'}
        </p>
        <h3>{draft.title || 'Untitled event'}</h3>
        <p className="meta">
          {draft.venue ? `${draft.venue}, ${draft.city}` : 'Venue not set yet'}
        </p>
        {tags.length > 0 && (
          <div className="tag-row">
            {tags.map((t, i) => (
              <span key={i} className="tag">{t}</span>
            ))}
          </div>
        )}
      </div>
    </article>
  );
}