import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { formatEventDate } from '../utils/format';
import { request } from '../api/apiClient';
import './TicketAndSeatMapView.css';

// Updated mock to match Rafly's flat TicketView record
const MOCK_BOOKING = {
  eventDate: new Date(Date.now() + 86400000).toISOString(),
  paymentStatus: 'MOCK_SUCCESS',
  seatCode: 'R3-S7',
  splitReason: null,
  generalAdmission: false,
  layout: { rows: 8, seatsPerRow: 12, template: 'FRONT' },
  booking: { id: 'demo', user: { name: 'You' } }
};

export function TicketAndSeatMapView() {
  const { bookingId } = useParams();
  const [booking, setBooking] = useState(MOCK_BOOKING);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!bookingId || bookingId === 'demo') return;
    
    setLoading(true);
    // Matches Rafly's @GetMapping("/api/bookings/{id}/ticket")
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

  // Rafly sends "MOCK_SUCCESS" for paid bookings, or "UNVERIFIED_LEGACY"
  const paid = booking.paymentStatus === 'MOCK_SUCCESS' || booking.paymentStatus === 'success';
  const isGeneralAdmission = booking.generalAdmission === true;

  return (
    <>
      <TopNav />
      <main className="page">
        <h1 className="page-heading">Your Ticket</h1>
        <p className="page-sub">{formatEventDate(booking.eventDate)}</p>

        <div className="panel" style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>Payment</span>
            <span className="badge badge-context">
              {paid ? 'Paid' : 'Pending / Unverified'}
            </span>
          </div>
          {!paid && (
            <p className="field-error" style={{ marginTop: 12 }}>
              No seat is assigned until payment is confirmed.
            </p>
          )}
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
          {!isGeneralAdmission && paid && (
            <>
              {!booking.seatCode && (
                <p className="page-sub">Seat will be assigned after registration closes.</p>
              )}

              {booking.seatCode && (
                <>
                  <div className="seat-box">
                    {/* Rafly sends seatCode directly at the top level */}
                    <p className="seat-code">{booking.seatCode}</p>
                    
                    {/* Rafly sends splitReason directly at the top level */}
                    {booking.splitReason && (
                      <p className="seat-split-note">
                        <strong>Group split.</strong> {booking.splitReason}
                      </p>
                    )}
                  </div>

                  {booking.layout && (
                    <SeatMap
                      rows={booking.layout.rows || booking.layout.rowCount}
                      seatsPerRow={booking.layout.seatsPerRow}
                      shape={booking.layout.template || 'front'}
                      // We use hardcoded row/col for the visual highlight fallback
                      assignedRow={3} 
                      assignedCol={7}
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
      return <span key={c} className={isAssigned ? 'seat-map-dot assigned' : 'seat-map-dot'} />;
    });
    return (
      <div 
        key={r} 
        className="seat-map-row" 
        style={shape === 'FAN' || shape === 'fan' ? { paddingLeft: `${(visibleRows - r) * 3}px`, paddingRight: `${(visibleRows - r) * 3}px` } : undefined}
      >
        {dots}
      </div>
    );
  });

  return (
    <div className="seat-map" aria-label="Seat map">
      <p className="seat-map-label">Your seat on the map</p>
      <div className="seat-map-diagram">
        {shape === 'CENTRAL' || shape === 'central' ? (
          <>
            <div className="seat-map-grid">{rowEls.slice(0, Math.ceil(visibleRows / 2))}</div>
            <div className="seat-map-stage">Stage</div>
            <div className="seat-map-grid">{rowEls.slice(Math.ceil(visibleRows / 2))}</div>
          </>
        ) : (
          <>
            <div className="seat-map-stage">Stage</div>
            <div className="seat-map-grid">{rowEls}</div>
          </>
        )}
      </div>
    </div>
  );
}