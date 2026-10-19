import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { formatEventDate } from '../utils/format';
import { request } from '../api/apiClient';
import './TicketAndSeatMapView.css';

const MOCK_BOOKING = {
  eventDate: new Date(Date.now() + 86400000).toISOString(),
  paymentStatus: 'MOCK_SUCCESS',
  seatCode: 'R3-S7',
  splitReason: null,
  generalAdmission: false,
  layout: { 
    rows: 6, 
    seatsPerRow: 10, 
    template: 'FRONT',
    // Added mock seats to match Rafly's SeatView record
    seats: [
      { id: 1, code: 'R3-S7', row: 2, column: 7, blocked: false, accessible: false },
      { id: 2, code: 'R1-S1', row: 0, column: 1, blocked: true, accessible: false }, // blocked mock
      { id: 3, code: 'R2-S2', row: 1, column: 2, blocked: false, accessible: true }  // accessible mock
    ]
  },
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
                      layout={booking.layout}
                      assignedSeatCode={booking.seatCode}
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

function SeatMap({ layout, assignedSeatCode }) {
  const shape = layout.template || 'front';
  const rows = layout.rows || layout.rowCount || 6;
  const seatsPerRow = layout.seatsPerRow || 10;

  const visibleRows = Math.min(Math.max(rows, 3), 10);
  const visibleCols = Math.min(Math.max(seatsPerRow, 4), 16);

  // Use Rafly's seat array if available
  const seatData = layout.seats || [];

  const rowEls = Array.from({ length: visibleRows }).map((_, rIdx) => {
    const dots = Array.from({ length: visibleCols }).map((__, cIdx) => {
      const r = rIdx;
      const c = cIdx + 1;
      
      // Find this seat in Rafly's data
      const seat = seatData.find(s => s.row === r && s.column === c);
      
      const isAssigned = seat ? seat.code === assignedSeatCode : false;
      const isBlocked = seat ? seat.blocked : false;
      const isAccessible = seat ? seat.accessible : false;

      let className = 'seat-map-dot';
      if (isAssigned) className += ' assigned';
      else if (isBlocked) className += ' blocked';
      else if (isAccessible) className += ' accessible';

      return (
        <span 
          key={c} 
          className={className} 
          title={seat ? seat.code : `Row ${r}, Col ${c}`}
        />
      );
    });
    
    return (
      <div
        key={rIdx}
        className="seat-map-row"
        style={
          shape === 'FAN' || shape === 'fan'
            ? { paddingLeft: `${(visibleRows - rIdx) * 3}px`, paddingRight: `${(visibleRows - rIdx) * 3}px` }
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
      <div style={{ marginTop: '16px', display: 'flex', gap: '16px', fontSize: '12px', justifyContent: 'center' }}>
        <span><span style={{ display: 'inline-block', width: '10px', height: '10px', background: 'var(--accent-strong)', borderRadius: '50%', marginRight: '6px' }}></span>Your Seat</span>
        <span><span style={{ display: 'inline-block', width: '10px', height: '10px', background: '#ff4d4d', borderRadius: '50%', marginRight: '6px' }}></span>Blocked</span>
        <span><span style={{ display: 'inline-block', width: '10px', height: '10px', background: '#4da6ff', borderRadius: '50%', marginRight: '6px' }}></span>Accessible</span>
      </div>
    </div>
  );
}