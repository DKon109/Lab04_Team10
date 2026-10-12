import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { SeatMap } from '../components/SeatMap';
import { fetchTicket } from '../api/seatingApi';
import { formatEventDate } from '../utils/format';

/** Replaces the Sprint 2 mock with the signed-in attendee's persisted ticket. */
export function TicketAndSeatMapView() {
  const { bookingId } = useParams();
  const [ticket, setTicket] = useState(null), [error, setError] = useState(''), [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true; setError('');
    fetchTicket(bookingId).then(data => { if (active) setTicket(data); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [bookingId, version]);
  return <><TopNav /><main className="page"><Link className="back-link" to="/bookings">← My bookings</Link>
    <div className="section-intro"><p className="eyebrow">YOUR TIXORA TICKET</p><h1>{ticket?.booking.eventTitle || 'Your event ticket'}</h1>{ticket && <p>{formatEventDate(ticket.eventDate)} · {ticket.venue}</p>}</div>
    {error && <div className="form-banner error" role="alert">{error} <button className="btn" onClick={() => setVersion(v => v + 1)}>Try again</button></div>}
    {!ticket && !error && <p role="status">Loading your private ticket…</p>}
    {ticket && <div className="checkout-layout"><section className="panel checkout-panel"><span className="badge">{ticket.paymentStatus === 'MOCK_SUCCESS' ? 'Mock payment confirmed' : 'Legacy payment unverified'}</span><h2>{ticket.booking.status === 'ALLOCATED' ? 'You’re ready to go.' : 'Your place is recorded.'}</h2><p className="reference">{ticket.booking.bookingRef}</p>
      {ticket.paymentStatus !== 'MOCK_SUCCESS' && <p className="form-banner error">This older booking has no recorded checkout attempt. Contact the organiser before using it as a confirmed ticket.</p>}
      {ticket.booking.groupCode && <p>Private group: <strong>{ticket.booking.groupCode}</strong></p>}
      {ticket.booking.accessibilityRequired && <p>Accessible seating requested</p>}
      {ticket.booking.status === 'ALLOCATED' ? <><p className="eyebrow">{ticket.generalAdmission ? 'ADMISSION' : 'YOUR ASSIGNED SEAT'}</p><p className="assigned-seat">{ticket.generalAdmission ? 'General admission' : ticket.seatCode}</p>{ticket.splitReason && <div className="form-banner error"><strong>Your group is split.</strong><p>{ticket.splitReason}</p></div>}</> : <p className="form-banner success">{ticket.booking.status === 'CANCELLED' ? 'This booking is cancelled.' : 'Waiting for the organiser to close registration and run allocation. No seat has been assigned yet.'}</p>}
      <button className="btn btn-ghost" onClick={() => setVersion(v => v + 1)}>Refresh ticket status</button><p className="subtle">Demo ticket · no real charge. Only your own booking details are shown.</p>
    </section><section className="panel checkout-panel"><h2>{ticket.generalAdmission ? 'General admission' : 'Find your seat'}</h2>{ticket.generalAdmission ? <p>This event does not assign physical seats. Follow the organiser's arrival instructions.</p> : ticket.layout ? <SeatMap layout={ticket.layout} selectedCode={ticket.seatCode} /> : <p>The organiser has not saved a seat map yet.</p>}</section></div>}
  </main></>;
}
