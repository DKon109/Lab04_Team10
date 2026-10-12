import { useState } from 'react';
import { Link } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { createEvent } from '../api/eventsApi';

const EMPTY_FORM = {
  title: '',
  category: '',
  dateTime: '',
  venue: '',
  audience: 'USYD',
  organiserName: '',
  organiserType: 'SOCIETY',
  imageUrl: '',
  description: '',
  tags: '',
  capacity: 100,
  seatIdentifiersConfirmed: false,
};

/**
 * Event publishing surface. The dedicated venue/allocation dashboard is
 * maintained separately and is not represented as complete here.
 */
export function OrganiserPage() {
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [banner, setBanner] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (submitting) return;
    setBanner(null);
    setErrors({});
    setSubmitting(true);
    try {
      const created = await createEvent({
        title: form.title,
        category: form.category,
        dateTime: form.dateTime,
        venue: form.venue,
        city: 'Sydney',
        university: form.audience === 'USYD' ? 'USYD' : null,
        organiserName: form.organiserName,
        organiserType: form.organiserType,
        imageUrl: form.imageUrl || null,
        description: form.description || null,
        capacity: Number(form.capacity),
        seatIdentifiersConfirmed: form.seatIdentifiersConfirmed,
        tags: form.tags
          .split(',')
          .map((t) => t.trim().toLowerCase())
          .filter(Boolean),
      });
      setBanner({ type: 'success', text: `“${created.title}” is live in the feed.` });
      setForm(EMPTY_FORM);
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
        <h1 className="page-heading">Organiser dashboard</h1>
        <p className="page-sub">
          Post your event once and reach every student on Tixora. <Link to="/events">Back to the feed</Link>
        </p>

        <div className="organiser-grid">
          <section className="panel" aria-labelledby="post-event-heading">
            <h2 id="post-event-heading">Post an event</h2>
            {banner && (
              <div className={`form-banner ${banner.type}`} role="alert">
                {banner.text}
              </div>
            )}
            <form onSubmit={handleSubmit} noValidate>
              <div className="field">
                <label htmlFor="ev-title">Event title</label>
                <input id="ev-title" value={form.title} onChange={update('title')} required />
                {fieldError('title')}
              </div>

              <div className="form-row">
                <div className="field">
                  <label htmlFor="ev-datetime">Date and time</label>
                  <input
                    id="ev-datetime"
                    type="datetime-local"
                    value={form.dateTime}
                    onChange={update('dateTime')}
                    required
                  />
                  {fieldError('dateTime')}
                </div>
                <div className="field">
                  <label htmlFor="ev-venue">Venue</label>
                  <input id="ev-venue" value={form.venue} onChange={update('venue')} required />
                  {fieldError('venue')}
                </div>
              </div>

              <div className="form-row">
                <div className="field">
                  <label htmlFor="ev-audience">Audience</label>
                  <select id="ev-audience" value={form.audience} onChange={update('audience')}>
                    <option value="USYD">USYD campus</option>
                    <option value="SYDNEY">All Sydney</option>
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="ev-category">Category</label>
                  <input
                    id="ev-category"
                    value={form.category}
                    onChange={update('category')}
                    placeholder="e.g. Technology, Careers, Music"
                    required
                  />
                  {fieldError('category')}
                </div>
              </div>

              <div className="form-row">
                <div className="field">
                  <label htmlFor="ev-organiser">Organiser name</label>
                  <input
                    id="ev-organiser"
                    value={form.organiserName}
                    onChange={update('organiserName')}
                    required
                  />
                  {fieldError('organiserName')}
                </div>
                <div className="field">
                  <label htmlFor="ev-organiser-type">Organiser type</label>
                  <select
                    id="ev-organiser-type"
                    value={form.organiserType}
                    onChange={update('organiserType')}
                  >
                    <option value="SOCIETY">Society</option>
                    <option value="FACULTY">Faculty</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
              </div>

              <div className="field">
                <label htmlFor="ev-image">Image URL (optional)</label>
                <input
                  id="ev-image"
                  type="url"
                  value={form.imageUrl}
                  onChange={update('imageUrl')}
                  placeholder="https://…"
                />
                {fieldError('imageUrl')}
              </div>

              <label className="field">Capacity (1–300)<input type="number" min="1" max="300" required value={form.capacity} onChange={update('capacity')} />{fieldError('capacity')}</label>
              <label className="check-label"><input type="checkbox" checked={form.seatIdentifiersConfirmed} onChange={e => setForm(previous => ({ ...previous, seatIdentifiersConfirmed: e.target.checked }))} /><span>I confirm permanent or temporary physical seat identifiers will be available at this venue.<span className="subtle">Required for private group booking. Leave unchecked for general admission; this does not create a seat map or run allocation.</span></span></label>

              <div className="field">
                <label htmlFor="ev-description">Description</label>
                <textarea
                  id="ev-description"
                  rows={4}
                  value={form.description}
                  onChange={update('description')}
                  placeholder="What should people know before they come?"
                />
                {fieldError('description')}
              </div>

              <div className="field">
                <label htmlFor="ev-tags">Tags (comma separated)</label>
                <input
                  id="ev-tags"
                  value={form.tags}
                  onChange={update('tags')}
                  placeholder="networking, free-food, workshop"
                />
              </div>

              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Publishing…' : 'Publish event'}
              </button>
            </form>
          </section>

          <section className="panel placeholder-panel" aria-labelledby="attendees-heading">
            <p className="eyebrow">SPRINT 3 · ATTENDEE EXPERIENCE</p>
            <h2 id="attendees-heading">From discovery to confirmed plans.</h2>
            <p>
              Attendees can now book individually or with a private group, complete mock
              checkout and review their booking status in My bookings.
            </p>
            <p>
              Organiser venue mapping, allocation controls and seat-map tickets still require
              integration with the team's dedicated dashboard. Checkout does not assign seats.
            </p>
            <Link className="btn btn-primary" to="/events">Preview attendee experience →</Link>
          </section>
        </div>
      </main>
    </>
  );
}
