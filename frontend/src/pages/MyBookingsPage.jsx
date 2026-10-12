import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchMyBookings } from '../api/bookingsApi';
import { TopNav } from '../components/layout/TopNav';

const statuses = { PENDING_ALLOCATION: 'Awaiting seat allocation', ALLOCATED: 'Allocated', CANCELLED: 'Cancelled' };
export default function MyBookingsPage() {
  const [bookings, setBookings] = useState(null);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true; setError('');
    fetchMyBookings().then(data => { if (active) setBookings(data); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [version]);
  return <><TopNav /><main className="page"><div className="section-intro"><p className="eyebrow">YOUR PLANS, IN ONE PLACE</p><h1>Good times ahead.</h1><p>Booking confirmations, private groups and your allocation status.</p></div>
    {error ? <div className="state-panel" role="alert"><p>{error}</p><button className="btn btn-primary" onClick={() => setVersion(v => v + 1)}>Try again</button></div> : !bookings ? <p role="status">Loading your bookings…</p> : !bookings.length ? <section className="state-panel"><h2>Your calendar has room for something good.</h2><p>Find an event and make your first booking.</p><Link to="/events" className="btn btn-primary">Explore events →</Link></section> : <div className="booking-list">{bookings.map(booking => <article className="booking-ticket" key={booking.id}>
      <div className="ticket-stub" aria-hidden="true">T<span>ADMIT ONE</span></div><div className="ticket-content"><span className="badge">{statuses[booking.status] || booking.status}</span><h2>{booking.eventTitle}</h2><p className="reference">{booking.bookingRef}</p>
      {booking.groupCode && <p>Private group: <strong>{booking.groupCode}</strong></p>}{booking.accessibilityRequired && <p>Accessible seating requested</p>}
      {booking.status === 'PENDING_ALLOCATION' && <p className="subtle">Your booking is recorded. Seats are assigned after registration closes; no seat has been assigned yet.</p>}
      <Link to={`/events/${booking.eventId}/book`} className="back-link">View event details →</Link></div>
    </article>)}</div>}
  </main></>;
}
