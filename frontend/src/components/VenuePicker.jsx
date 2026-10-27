import { useState, useEffect } from 'react';
import { request } from '../api/apiClient'; 
import './VenuePicker.css';

const TEMPLATES = [
  { id: 'front', name: 'Front stage', blurb: 'Rows facing a stage at the front.', shape: 'front' },
  { id: 'fan', name: 'Fan / semicircle', blurb: 'Curved rows around a stage at the front.', shape: 'fan' },
  { id: 'central', name: 'Central stage', blurb: 'Seats on both sides of a stage in the middle.', shape: 'central' },
];

export function buildRoomLayout(room) {
  return {
    mode: 'usyd',
    roomId: room.id,
    roomName: room.name,
    capacity: room.capacity,
    map: room.map, // <--- GAP 4 FIX: Pass the new managed map object
    shape: room.shape,
    rows: room.rows,
    seatsPerRow: room.seatsPerRow,
    hasNumberedSeats: room.hasNumberedSeats,
  };
}

export function buildTemplateLayout(template, config) {
  const capacity = (config.rows || 0) * (config.seatsPerRow || 0);
  if (capacity <= 0) return null;
  return {
    mode: 'external',
    templateId: template.id,
    templateName: template.name,
    capacity,
    shape: template.shape,
    rows: config.rows,
    seatsPerRow: config.seatsPerRow,
    hasNumberedSeats: config.hasNumberedSeats,
  };
}

export function VenuePicker({ value, onSelect }) {
  const [mode, setMode] = useState(value?.mode || 'usyd');
  const [roomId, setRoomId] = useState(value?.roomId || null);
  const [templateId, setTemplateId] = useState(value?.templateId || null);
  const [config, setConfig] = useState({
    rows: value?.rows || 6,
    seatsPerRow: value?.seatsPerRow || 10,
    hasNumberedSeats: value?.hasNumberedSeats || false,
  });

  // --- EXPLICIT API STATES ---
  const [realRooms, setRealRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    
    // GAP 4 FIX: Fetch from the new campus template endpoint
    request('/organiser/venue-templates')
      .then((data) => {
        if (cancelled) return;
        if (data && data.length > 0) {
          const mappedRooms = data.map(room => {
            // Extract capacity and dimensions from the managed map if possible, fallback to defaults
            const seatCount = room.map?.seats?.length || room.map?.capacity || 0;
            const numRows = room.map?.rows?.length || Math.max(1, Math.ceil(seatCount / 15));
            const numSeatsPerRow = room.map?.seatsPerRow || Math.ceil(seatCount / numRows) || 15;

            return {
              id: room.id,
              name: room.name,
              building: room.sourceUrl ? 'Verified Plan' : 'Campus Room',
              capacity: seatCount,
              map: room.map, // Keep the raw map data
              rows: numRows,
              seatsPerRow: numSeatsPerRow,
              hasNumberedSeats: false, // Backend dictates this starts unverified
              shape: 'front'
            };
          });
          setRealRooms(mappedRooms);
        } else {
          setRealRooms([]);
        }
      })
      .catch((err) => {
        if (cancelled) return;
        console.error(err);
        setError("Failed to load venue rooms. Please check your connection and try again.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, []);
  // --------------------------------------------------

  const room = realRooms.find((r) => r.id === roomId) || null;
  const template = TEMPLATES.find((t) => t.id === templateId) || null;

  const capacity =
    mode === 'usyd'
      ? room?.capacity || 0
      : (config.rows || 0) * (config.seatsPerRow || 0);

  function emit(layout) {
    if (layout && onSelect) onSelect(layout);
  }

  function pickMode(next) {
    setMode(next);
    if (next === 'usyd' && room) emit(buildRoomLayout(room));
    if (next === 'external' && template) emit(buildTemplateLayout(template, config));
  }

  function pickRoom(id) {
    setRoomId(id);
    const r = realRooms.find((x) => x.id === id);
    if (r) emit(buildRoomLayout(r));
  }

  function pickTemplate(id) {
    setTemplateId(id);
    const t = TEMPLATES.find((x) => x.id === id);
    if (t) emit(buildTemplateLayout(t, config));
  }

  function updateConfig(patch) {
    const next = { ...config, ...patch };
    setConfig(next);
    if (mode === 'external' && template) {
      emit(buildTemplateLayout(template, next));
    }
  }

  const previewShape = mode === 'usyd' ? room?.shape : template?.shape;
  const previewRows = mode === 'usyd' ? room?.rows : config.rows;
  const previewCols = mode === 'usyd' ? room?.seatsPerRow : config.seatsPerRow;

  return (
    <div className="venue-picker">
      <div className="segmented venue-mode-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-pressed={mode === 'usyd'}
          onClick={() => pickMode('usyd')}
        >
          USYD room
        </button>
        <button
          type="button"
          role="tab"
          aria-pressed={mode === 'external'}
          onClick={() => pickMode('external')}
        >
          External template
        </button>
      </div>

      {mode === 'usyd' && (
        <>
          {loading && <p className="results-note">Loading rooms...</p>}
          
          {error && <div className="form-banner error">{error}</div>}
          
          {!loading && !error && realRooms.length === 0 && (
            <p className="results-note">No rooms available for this event.</p>
          )}

          {!loading && !error && realRooms.length > 0 && (
            <div className="venue-grid">
              {realRooms.map((r) => {
                const selected = roomId === r.id;
                return (
                  <button
                    key={r.id}
                    type="button"
                    className={`venue-card ${selected ? 'selected' : ''}`}
                    onClick={() => pickRoom(r.id)}
                    aria-pressed={selected}
                  >
                    <span className="venue-card-name">{r.name}</span>
                    <span className="venue-card-meta">
                      {r.building} · {r.capacity} seats
                    </span>
                    {r.hasNumberedSeats === false && (
                      <span className="venue-card-flag">Requires physical verification</span>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </>
      )}

      {mode === 'external' && (
        <>
          <div className="venue-grid">
            {TEMPLATES.map((t) => {
              const selected = templateId === t.id;
              return (
                <button
                  key={t.id}
                  type="button"
                  className={`venue-card ${selected ? 'selected' : ''}`}
                  onClick={() => pickTemplate(t.id)}
                  aria-pressed={selected}
                >
                  <span className="venue-card-name">{t.name}</span>
                  <span className="venue-card-meta">{t.blurb}</span>
                </button>
              );
            })}
          </div>

          {template && (
            <div className="venue-config">
              <div className="form-row">
                <div className="field">
                  <label htmlFor="vp-rows">Rows</label>
                  <input
                    id="vp-rows"
                    type="number"
                    min="1"
                    value={config.rows}
                    onChange={(e) =>
                      updateConfig({ rows: Math.max(0, Number(e.target.value) || 0) })
                    }
                  />
                </div>
                <div className="field">
                  <label htmlFor="vp-seats">Seats per row</label>
                  <input
                    id="vp-seats"
                    type="number"
                    min="1"
                    value={config.seatsPerRow}
                    onChange={(e) =>
                      updateConfig({ seatsPerRow: Math.max(0, Number(e.target.value) || 0) })
                    }
                  />
                </div>
              </div>

              <label className="venue-checkbox">
                <input
                  type="checkbox"
                  checked={config.hasNumberedSeats}
                  onChange={(e) => updateConfig({ hasNumberedSeats: e.target.checked })}
                />
                Seats are numbered
              </label>
            </div>
          )}
        </>
      )}

      {previewShape && (
        <div className="venue-preview">
          <p className="venue-preview-label">Layout preview</p>
          <LayoutPreview
            shape={previewShape}
            rows={previewRows}
            seatsPerRow={previewCols}
          />
          {capacity > 0 && (
            <p className="venue-preview-capacity">
              Capacity: <strong>{capacity}</strong>
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function LayoutPreview({ shape = 'front', rows = 6, seatsPerRow = 10 }) {
  const visibleRows = Math.min(Math.max(rows || 1, 3), 8);
  const visibleCols = Math.min(Math.max(seatsPerRow || 1, 4), 14);

  const rowEls = Array.from({ length: visibleRows }).map((_, i) => (
    <div
      key={i}
      className="seat-row"
      style={
        shape === 'fan'
          ? {
              paddingLeft: `${(visibleRows - i) * 3}px`,
              paddingRight: `${(visibleRows - i) * 3}px`,
            }
          : undefined
      }
    >
      {Array.from({ length: visibleCols }).map((__, j) => (
        <span key={j} className="seat-dot" />
      ))}
    </div>
  ));

  if (shape === 'central') {
    const half = Math.ceil(visibleRows / 2);
    return (
      <div className="layout-diagram" aria-hidden="true">
        <div className="seat-grid">{rowEls.slice(0, half)}</div>
        <div className="stage-bar">Stage</div>
        <div className="seat-grid">{rowEls.slice(half)}</div>
      </div>
    );
  }

  return (
    <div className="layout-diagram" aria-hidden="true">
      <div className="stage-bar">Stage</div>
      <div className="seat-grid">{rowEls}</div>
    </div>
  );
}