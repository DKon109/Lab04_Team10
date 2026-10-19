import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { formatEventDate } from '../utils/format';
import { request } from '../api/apiClient'; // Added to fetch real data
import './TicketAndSeatMapView.css';

const MOCK_BOOKING = {
  id: 'demo',
  eventTitle: 'Demo event',
  eventDate: new Date(Date.now() + 86400000).toISOString(),
  paymentStatus: 'success',
  seatingMode: 'group-allocated', // Added this for the GA check
  allocationRun: true,
  seat: { code: 'R3-S7', row: 3, col: 7, split: false },
  layout: { rows: 8, seatsPerRow: 12, shape: 'front' },
  members: [
    { id: 'you', name: 'You', isYou: true },
    { id: 'friend', name: 'Alex', isYou: false },
  ],
};

const PAYMENT_LABEL = {
  pending: 'Awaiting payment',
  success: 'Paid',
  declined: 'Payment declined',
  cancelled: 'Booking cancelled',
};

export function TicketAndSeatMapView() {
  const { bookingId } = useParams();
  const [booking, setBooking] = useState(MOCK_BOOKING);
  const [loading, setLoading] = useState(false);

  // Fetch real booking
  useEffect(() => {
    if (!bookingId || bookingId === 'demo') return;
    
    setLoading(true);
    // UPDATED: Matches Rafly's @GetMapping("/api/bookings/{id}/ticket")
    request(`/bookings/${bookingId}/ticket`)
      .then((data) => {
        if (data) setBooking(data);
        setLoading(false);
      })
      .catch((err) => {
        console.warn("Using mock fallback", err);
        setLoading(false);
      });
  }, [bookingId]);

  if (loading) return <p className="page-sub">Loading ticket...</p>;
  if (!booking) return null;

  const paid = booking.paymentStatus === 'success';
  const allocationDone = paid && booking.allocationRun;
  const hasSeat = allocationDone && booking.seat;
  
  // General Admission Check
  const isGeneralAdmission = booking.seatingMode === 'general-admission';

  return (
    <>
      <TopNav />
      <main className="page">
        <h1 className="page-heading">{booking.eventTitle}</h1>
        <p className="page-sub">{formatEventDate(booking.eventDate)}</p>

        <div className="panel" style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>Payment</span>
            <span className="badge badge-context">
              {PAYMENT_LABEL[booking.paymentStatus] || booking.paymentStatus}
            </span>
          </div>
          {booking.paymentStatus === 'declined' && (
            <p className="field-error" style={{ marginTop: 12 }}>
              Your payment was declined. No seat has been assigned.
            </p>
          )}
          {booking.paymentStatus === 'cancelled' && (
            <p className="page-sub" style={{ marginTop: 12 }}>
              This booking has been cancelled.
            </p>
          )}
        </div>

        <div className="panel" style={{ marginBottom: 16 }}>
          <h2>Group</h2>
          <div className="tag-row">
            {(booking.members || []).map((m) => (
              <span
                key={m.id}
                className="tag"
                style={
                  m.isYou
                    ? { background: 'var(--accent-soft)', borderColor: 'var(--accent)' }
                    : undefined
                }
              >
                {m.name}
                {m.isYou ? ' (you)' : ''}
              </span>
            ))}
          </div>
        </div>

        <div className="panel">
          <h2>{isGeneralAdmission ? 'Ticket' : 'Seat'}</h2>

          {/* GENERAL ADMISSION BRANCH */}
          {isGeneralAdmission && paid && (
            <div className="seat-box" style={{ background: 'var(--surface)', borderColor: 'var(--accent-soft)' }}>
              <p style={{ fontSize: '1.2rem', fontWeight: 700, margin: 0, color: 'var(--accent-strong)' }}>
                General Admission
              </p>
              <p className="page-sub" style={{ margin: '8px 0 0' }}>
                Present this screen at the door for entry. No specific seat is assigned.
              </p>
            </div>
          )}

          {/* ALLOCATED SEAT BRANCH */}
          {!isGeneralAdmission && (
            <>
              {!paid && (
                <p className="page-sub">
                  No seat is assigned until payment is confirmed.
                </p>
              )}

              {paid && !allocationDone && (
                <p className="page-sub">
                  Seat will be assigned after registration closes.
                </p>
              )}

              {allocationDone && !booking.seat && (
                <p className="field-error">
                  Allocation has run but no seat came back for this booking.
                </p>
              )}

              {hasSeat && (
                <>
                  <div className="seat-box">
                    <p className="seat-code">{booking.seat.code}</p>
                    {booking.seat.split && (
                      <p className="seat-split-note">
                        <strong>Group split.</strong>{' '}
                        {booking.seat.splitReason ||
                          'Your group couldn\u2019t be seated together, so seats were allocated across two blocks.'}
                      </p>
                    )}
                  </div>

                  {booking.layout && (
                    <SeatMap
                      rows={booking.layout.rows}
                      seatsPerRow={booking.layout.seatsPerRow}
                      shape={booking.layout.shape}
                      assignedRow={booking.seat.row}
                      assignedCol={booking.seat.col}
                    />
                  )}
                </>
              )}
            </>
          )}
        </div>
      </main>
    </>
  );
}

function SeatMap({ rows, seatsPerRow, shape, assignedRow, assignedCol }) {
  const visibleRows = Math.min(Math.max(rows || 1, 3), 10);
  const visibleCols = Math.min(Math.max(seatsPerRow || 1, 4), 16);

  const assignedR = Math.min(Math.max(assignedRow || 1, 1), visibleRows);
  const assignedC = Math.min(Math.max(assignedCol || 1, 1), visibleCols);

  const rowEls = Array.from({ length: visibleRows }).map((_, rIdx) => {
    const r = rIdx + 1;
    const dots = Array.from({ length: visibleCols }).map((__, cIdx) => {
      const c = cIdx + 1;
      const isAssigned = r === assignedR && c === assignedC;
      return (
        <span
          key={c}
          className={isAssigned ? 'seat-map-dot assigned' : 'seat-map-dot'}
        />
      );
    });
    return (
      <div
        key={r}
        className="seat-map-row"
        style={
          shape === 'fan'
            ? {
                paddingLeft: `${(visibleRows - r) * 3}px`,
                paddingRight: `${(visibleRows - r) * 3}px`,
              }
            : undefined
        }
      >
        {dots}
      </div>
    );
  });

  return (
    <div className="seat-map" aria-label="Seat map">
      <p className="seat-map-label">Your seat on the map</p>
      <div className="seat-map-diagram">
        {shape === 'central' ? (
          <>
            <div className="seat-map-grid">
              {rowEls.slice(0, Math.ceil(visibleRows / 2))}
            </div>
            <div className="seat-map-stage">Stage</div>
            <div className="seat-map-grid">
              {rowEls.slice(Math.ceil(visibleRows / 2))}
            </div>
          </>
        ) : (
          <>
            <div className="seat-map-stage">Stage</div>
            <div className="seat-map-grid">{rowEls}</div>
          </>
        )}
      </div>
      <p className="seat-map-caption">
        Row {assignedRow}, seat {assignedCol}
      </p>
    </div>
  );
}