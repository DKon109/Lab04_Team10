import { useState, useEffect } from 'react';
import { request } from '../api/apiClient'; // Added to fetch real data
import './VenuePicker.css';

const MOCK_ROOMS = [
  { id: 'woolley', name: 'Woolley Lecture Theatre', building: 'Quadrangle', capacity: 350, rows: 14, seatsPerRow: 25, hasNumberedSeats: true, shape: 'front' },
  { id: 'sit-lt1', name: 'SIT Lecture Theatre 1', building: 'SIT Building', capacity: 125, rows: 9, seatsPerRow: 14, hasNumberedSeats: true, shape: 'fan' },
  { id: 'manning', name: 'Manning Bar', building: 'Manning House', capacity: 200, rows: 10, seatsPerRow: 20, hasNumberedSeats: false, shape: 'front' },
];

const TEMPLATES = [
  { id: 'front', name: 'Front stage', blurb: 'Rows facing a stage at the front.', shape: 'front' },
  { id: 'fan', name: 'Fan / semicircle', blurb: 'Curved rows around a stage at the front.', shape: 'fan' },
  { id: 'central', name: 'Central stage', blurb: 'Seats on both sides of a stage in the middle.', shape: 'central' },
];

// Exported for unit tests — pure function.
export function buildRoomLayout(room) {
  return {
    mode: 'usyd',
    roomId: room.id,
    roomName: room.name,
    capacity: room.capacity,
    shape: room.shape,
    rows: room.rows,
    seatsPerRow: room.seatsPerRow,
    hasNumberedSeats: room.hasNumberedSeats,
  };
}

// Exported for unit tests — pure function.
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

  // --- NEW: Fetch real rooms from Ryoji's backend ---
  const [realRooms, setRealRooms] = useState(MOCK_ROOMS); 
  
  useEffect(() => {
    request('/venues')
      .then((data) => {
        if (data && data.length > 0) {
          setRealRooms(data);
        }
      })
      .catch((err) => {
        console.warn("Using mock rooms: /venues endpoint not ready", err);
      });
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
        <div className="venue-grid">
          {/* CHANGED: Mapping over realRooms instead of MOCK_ROOMS */}
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
                  <span className="venue-card-flag">No numbered seats</span>
                )}
              </button>
            );
          })}
        </div>
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