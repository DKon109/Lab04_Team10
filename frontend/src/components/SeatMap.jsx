export function SeatMap({ layout, selectedCode }) {
  if (!layout) return null;
  const rows = Array.from({ length: layout.rows }, (_, row) => layout.seats.filter(seat => seat.row === row));
  const stage = <div className="live-stage">STAGE</div>;
  return <div className={`live-seat-map shape-${layout.template.toLowerCase()}`} role="img" aria-label={`Seat map. ${selectedCode ? `Your seat is ${selectedCode}.` : 'Seats are assigned after registration closes.'} Blocked seats and accessible spaces are marked.`}>
    {layout.template !== 'CENTRAL' && stage}
    {rows.map((seats, row) => <div key={row}>
      {layout.template === 'CENTRAL' && row === Math.ceil(layout.rows / 2) && stage}
      <div className="live-seat-row"><span className="row-label">{String.fromCharCode(65 + row)}</span>{seats.map(seat => <span key={seat.code} title={`${seat.code}${seat.blocked ? ' · Blocked' : ''}${seat.accessible ? ' · Accessible' : ''}${seat.code === selectedCode ? ' · Your seat' : ''}`} className={`live-seat ${seat.blocked ? 'blocked' : ''} ${seat.accessible ? 'accessible' : ''} ${seat.code === selectedCode ? 'yours' : ''} ${layout.aisleAfter > 0 && seat.column === layout.aisleAfter ? 'aisle-after' : ''}`}>{seat.blocked ? '×' : seat.code === selectedCode ? '●' : seat.column}<span className="visually-hidden">{seat.code}</span></span>)}</div>
    </div>)}
    <p className="map-legend">● Your seat · × Blocked · Outlined spaces are accessible. Gaps indicate aisles.</p>
    <p className="subtle">Schematic, not to scale · Layout version {layout.version}</p>
    {layout.source === 'CAMPUS_DEMO' && <p className="form-banner error">Campus demo layout — not a verified physical room map.</p>}
  </div>;
}
