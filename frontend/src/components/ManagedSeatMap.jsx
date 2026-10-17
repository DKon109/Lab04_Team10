export function ManagedSeatMap({ map, selectedCode }) {
  const points = map.elements.flatMap(element => [element, ...element.seats]);
  const xs = points.map(point => point.positionX), ys = points.map(point => point.positionY);
  const left = Math.min(0, ...xs) - 35, top = Math.min(0, ...ys) - 35;
  const width = Math.max(0, ...xs) - left + 35, height = Math.max(0, ...ys) - top + 35;
  return <div className="live-seat-map">
    <h3>{map.name}</h3>
    <svg viewBox={`${left} ${top} ${width} ${height}`} style={{ width: '100%', maxHeight: 480 }} role="img"
      aria-label={`Seat map: ${map.name}. ${selectedCode ? `Your seat is ${selectedCode}.` : 'No seat assigned yet.'}`}>
      {map.elements.map(element => <g key={element.key}>
        <text x={element.positionX} y={element.positionY - 15} fontSize="8" textAnchor="middle"
          transform={`rotate(${element.rotationDegrees} ${element.positionX} ${element.positionY - 15})`}>{element.label}</text>
        {element.seats.map(seat => {
          const selected = seat.code === selectedCode;
          return <g key={seat.code} transform={`translate(${seat.positionX} ${seat.positionY})`}>
            <title>{`${seat.code}${selected ? ' · Your seat' : ''}${seat.accessible ? ' · Accessible' : ''} · ${seat.status}`}</title>
            <circle r="4" fill={selected ? '#2563eb' : seat.status === 'BLOCKED' ? '#64748b' : '#e2e8f0'}
              stroke={seat.accessible ? '#047857' : '#334155'} strokeWidth={seat.accessible ? 2 : 1} />
            <text y="11" textAnchor="middle" fontSize="6">{seat.label || seat.code}</text>
          </g>;
        })}
      </g>)}
    </svg>
    <p className="map-legend">Blue: your seat · Grey: blocked · Green outline: accessible</p>
    <p className="subtle">Schematic, not to scale · Map version {map.version}</p>
  </div>;
}
