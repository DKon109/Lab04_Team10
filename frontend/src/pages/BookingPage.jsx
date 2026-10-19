import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchEvent } from '../api/eventsApi';
import { mockCheckout } from '../api/bookingsApi';
import { TopNav } from '../components/layout/TopNav';
import { EventImage } from '../components/common/EventImage';
import { formatEventDate } from '../utils/format';

export default function BookingPage() {
  const { eventId } = useParams();
  const [event, setEvent] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [mode, setMode] = useState('solo');
  const [group, setGroup] = useState('');
  const [accessible, setAccessible] = useState(false);
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const attempt = useRef(null);
  const submitting = useRef(false);
  useEffect(() => {
    let active = true;
    setEvent(null); setLoadError(''); setMode('solo'); setGroup(''); setAccessible(false);
    setReview(false); setError(''); setResult(null); attempt.current = null;
    fetchEvent(eventId).then(data => { if (active) setEvent(data); }).catch(e => { if (active) setLoadError(e.message); });
    return () => { active = false; };
  }, [eventId]);

  async function checkout(outcome) {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true); setError('');
    // Preserve the exact request after an ambiguous network failure. A retry may
    // retrieve an already-committed booking, but cannot create another one.
    const body = attempt.current || {
      createGroup: mode === 'create', joinGroupCode: mode === 'join' ? group.trim().toUpperCase() : null,
      accessibilityRequired: accessible, outcome, idempotencyKey: crypto.randomUUID(),
    };
    attempt.current = body;
    try {
      setResult(await mockCheckout(eventId, body));
      attempt.current = null;
    } catch (e) {
      setError(e.message);
      if (e.status >= 400 && e.status < 500) attempt.current = null;
    } finally { setBusy(false); submitting.current = false; }
  }
  const closed = event && (event.registrationStatus === 'CLOSED' || new Date(event.dateTime) <= new Date());
  return <><TopNav /><main className="page booking-page">
    <Link className="back-link" to="/events">← Back to discovery</Link>
    <div className="section-intro"><p className="eyebrow">MAKE IT A PLAN</p><h1>Your next good memory.</h1><p>One booking. Something to look forward to.</p></div>
    {loadError ? <div className="state-panel" role="alert">{loadError} <button className="btn" onClick={() => window.location.reload()}>Try again</button></div> : !event ? <p role="status">Loading event…</p> :
    <div className="checkout-layout"><aside className="booking-summary panel">
      <div className="summary-image"><EventImage src={event.imageUrl} title={event.title} alt="" /></div>
      <span className="badge">{event.category}</span><h2>{event.title}</h2>
      <dl className="detail-list"><dt>When</dt><dd>{formatEventDate(event.dateTime)}</dd><dt>Where</dt><dd>{event.venue}</dd><dt>By</dt><dd>{event.organiserName}</dd></dl>
      <p className="subtle">Seats are not chosen at checkout. Where assigned seating is supported, allocation takes place after registration closes. Already booked? <Link to="/bookings">Check My bookings</Link>.</p>
    </aside><section className="panel checkout-panel" aria-label="Booking checkout">
      {result?.outcome === 'SUCCESS' ? <div className="confirmation" role="status">
        <span className="confirmation-mark" aria-hidden="true">✓</span><p className="eyebrow">YOU’RE ON THE LIST</p><h2>Booking confirmed.</h2>
        <p>Mock checkout succeeded. No money was charged.</p><p className="reference">{result.booking.bookingRef}</p>
        {result.booking.groupCode && <div className="group-share"><p>Your private event group</p><strong>{result.booking.groupCode}</strong><p>Share this code only with friends booking this same event. Each person completes their own booking.</p></div>}
        <p>Seat allocation is pending. This confirmation is not a seat assignment.</p><Link className="btn btn-primary" to="/bookings">View my bookings →</Link>
      </div> : result ? <div role="status"><p className="eyebrow">MOCK CHECKOUT</p><h2>{result.outcome === 'DECLINED' ? 'Payment declined.' : 'Checkout cancelled.'}</h2><p>No booking was created and no capacity was used. No money was charged.</p><button className="btn btn-primary" onClick={() => { setResult(null); setReview(false); }}>Return to booking</button></div> : closed ? <div role="status"><h2>Registration is unavailable.</h2><p>This event has closed registration or already started. Discover another event instead.</p><Link className="btn btn-primary" to="/events">Explore events</Link></div> : <>
      <ol className="checkout-steps" aria-label="Booking progress"><li aria-current={!review ? 'step' : undefined}>01 · Your booking</li><li aria-current={review ? 'step' : undefined}>02 · Mock checkout</li></ol>
      {!review ? <form onSubmit={e => { e.preventDefault(); setReview(true); setError(''); }}>
        <h2>Who are you going with?</h2><p className="subtle">Reserve one place for yourself, solo or as part of a private group.</p>
        <fieldset className="booking-options"><legend className="visually-hidden">Booking type</legend>
          {[['solo', 'Just me', 'An individual booking. Your own adventure.'], ...(event.seatIdentifiersConfirmed ? [['create', 'Start a group', 'Get a private code to share with friends.'], ['join', 'Join my friends', 'Use a group code for this event.']] : [])].map(([value, title, description]) => <label className={`booking-option ${mode === value ? 'selected' : ''}`} key={value}><input type="radio" name="mode" value={value} checked={mode === value} onChange={() => setMode(value)} /><span><strong>{title}</strong><small>{description}</small></span></label>)}
        </fieldset>
        {!event.seatIdentifiersConfirmed && <p className="subtle">Individual booking only: the organiser has not confirmed physical seat identifiers for private group seating.</p>}
        {mode === 'join' && <label className="field">Group code<input required maxLength={32} autoComplete="off" value={group} onChange={e => setGroup(e.target.value)} pattern=".*\S.*" placeholder="GRP-XXXXXXXX" /></label>}
        <label className="check-label"><input type="checkbox" checked={accessible} onChange={e => setAccessible(e.target.checked)} /><span>I require accessible seating<span className="subtle">We’ll pass this requirement to the organiser. A particular seat is not guaranteed at this stage.</span></span></label>
        <button className="btn btn-primary btn-wide" type="submit">Continue to mock checkout →</button>
      </form> : <><p className="eyebrow">DEMO ONLY · NO REAL CHARGES</p><h2>Ready to make it happen?</h2><p>This project uses simulated payments. Choose an outcome to complete or cancel your booking.</p>
        <dl className="review-list"><dt>Booking</dt><dd>{mode === 'solo' ? 'Individual · 1 attendee' : mode === 'create' ? 'New private group · 1 attendee' : `Join ${group.trim().toUpperCase()} · 1 attendee`}</dd><dt>Accessibility</dt><dd>{accessible ? 'Accessible seating requested' : 'Not requested'}</dd></dl>
        {error && <p className="form-banner error" role="alert">{error}{attempt.current && ' The result is uncertain. Retry the same attempt or check My bookings before starting over.'}</p>}
        {attempt.current && !busy ? <button className="btn btn-primary btn-wide" onClick={() => checkout(attempt.current.outcome)}>Retry same checkout</button> : <div className="checkout-actions"><button disabled={busy} className="btn btn-primary btn-wide" onClick={() => checkout('SUCCESS')}>{busy ? 'Processing…' : 'Simulate successful payment →'}</button><button disabled={busy} className="btn btn-ghost" onClick={() => checkout('DECLINED')}>Simulate decline</button><button disabled={busy} className="btn btn-ghost" onClick={() => checkout('CANCELLED')}>Cancel checkout</button></div>}
        <button disabled={busy || !!attempt.current} className="back-link" onClick={() => { setReview(false); setError(''); }}>← Edit booking details</button>
        <p className="subtle" role="status">{busy ? 'Saving your checkout. Please wait…' : 'No card details required. Nothing will be charged.'}</p>
      </>}
      </>}
    </section></div>}
  </main></>;
}
