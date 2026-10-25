import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { TopNav } from '../components/layout/TopNav';
import { SeatMap } from '../components/SeatMap';
import { fetchSeating, saveSeating, allocateSeats, fetchSeatingProfiles, fetchAllocationExport } from '../api/seatingApi';
import { setEventStatus } from '../api/organiserApi';

const initialForm = { source: 'EXTERNAL', template: 'FRONT', roomId: '', rows: 6, seatsPerRow: 10, aisleAfter: 5, blockedCodes: '', accessibleCodes: 'A1, A2', identifiersConfirmed: false };
export default function EventSeatingPage() {
  const { eventId } = useParams();
  const [data, setData] = useState(null), [profiles, setProfiles] = useState([]), [form, setForm] = useState(initialForm);
  const [error, setError] = useState(''), [message, setMessage] = useState(''), [busy, setBusy] = useState(false), [confirmClose, setConfirmClose] = useState(false);
  const [loadVersion, setLoadVersion] = useState(0);
  const [strategy, setStrategy] = useState('DEFAULT');
  useEffect(() => {
    let active = true;
    Promise.all([fetchSeating(eventId), fetchSeatingProfiles()]).then(([next, options]) => {
      if (!active) return; setData(next); setProfiles(options); setError('');
      if (next.layout) setForm({ ...next.layout, blockedCodes: next.layout.seats.filter(s => s.blocked).map(s => s.code).join(', '), accessibleCodes: next.layout.seats.filter(s => s.accessible).map(s => s.code).join(', '), identifiersConfirmed: next.event.seatIdentifiersConfirmed });
    }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [eventId, loadVersion]);
  const update = key => e => setForm(previous => ({ ...previous, [key]: e.target.value }));
  async function action(operation, successMessage) {
    if (busy) return; setBusy(true); setError(''); setMessage('');
    try { await operation(); setData(await fetchSeating(eventId)); setMessage(successMessage); setConfirmClose(false); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function download() {
    await action(async () => {
      const csv = await fetchAllocationExport(eventId);
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
      const link = document.createElement('a'); link.href = url; link.download = `event-${eventId}-allocation.csv`; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }, 'Anonymous allocation CSV downloaded. No attendee names or emails are included.');
  }
  const draft = data?.event.status === 'draft';
  return <><TopNav /><main className="page"><Link className="back-link" to="/organiser/dashboard">← My events</Link><div className="section-intro"><p className="eyebrow">ORGANISER WORKSPACE</p><h1>{data?.event.title || 'Event seating'}</h1><p>Configure. Publish. Close registration. Allocate.</p></div>
    {error && <div className="form-banner error" role="alert">{error} {!data && <button className="btn" onClick={() => setLoadVersion(v => v + 1)}>Try again</button>}</div>}
    {message && <p className="form-banner success" role="status">{message}</p>}
    {!data && !error && <p role="status">Loading organiser workspace…</p>}
    {data && <><div className="management-stats"><div><small>EVENT STATUS</small><strong>{data.event.status}</strong></div><div><small>CONFIRMED BOOKINGS</small><strong>{data.confirmedBookings} / {data.event.capacity}</strong></div><div><small>SEATING</small><strong>{data.event.seatIdentifiersConfirmed ? 'Assigned seats' : 'General admission'}</strong></div></div>
      {data.unverifiedLegacyBookings > 0 && <p className="form-banner error">{data.unverifiedLegacyBookings} legacy bookings have no recorded mock-success attempt and are excluded from allocation. Reconcile these before the final demo.</p>}
      <div className="checkout-layout"><section className="panel checkout-panel"><h2>{draft ? 'Set up your venue' : 'Locked venue configuration'}</h2>
      {draft ? <form onSubmit={e => { e.preventDefault(); action(() => saveSeating(eventId, { ...form, rows: Number(form.rows), seatsPerRow: Number(form.seatsPerRow), aisleAfter: Number(form.aisleAfter), blockedCodes: form.blockedCodes.split(',').map(s => s.trim()).filter(Boolean), accessibleCodes: form.accessibleCodes.split(',').map(s => s.trim()).filter(Boolean) }), 'Layout saved. Capacity now matches the usable seats.'); }}>
        <label className="field">Venue source<select value={form.source} onChange={update('source')}><option value="EXTERNAL">External venue template</option><option value="CAMPUS_DEMO">Campus demo profiles (unverified)</option></select></label>
        {form.source === 'CAMPUS_DEMO' ? <><p className="form-banner error">These three sample plans are illustrative. Verify actual room mappings before claiming the proposal's mapped-campus requirement is complete.</p><label className="field">Demo profile<select required value={form.roomId || ''} onChange={e => { const p = profiles.find(p => p.id === e.target.value); setForm(previous => ({ ...previous, roomId: p.id, template: p.template, rows: p.rows, seatsPerRow: p.seatsPerRow })); }}><option value="" disabled>Select profile</option>{profiles.map(p => <option key={p.id} value={p.id}>{p.name} · {p.rows * p.seatsPerRow} demo seats</option>)}</select></label></> : <><label className="field">Layout family<select value={form.template} onChange={update('template')}><option value="FRONT">Front stage</option><option value="FAN">Fan / semicircle</option><option value="CENTRAL">Central stage</option></select></label><div className="form-row"><label className="field">Rows<input required type="number" min="1" max="20" value={form.rows} onChange={update('rows')} /></label><label className="field">Seats per row<input required type="number" min="1" max="20" value={form.seatsPerRow} onChange={update('seatsPerRow')} /></label></div></>}
        <label className="field">Aisle after seat number (0 = no aisle)<input required type="number" min="0" max={Number(form.seatsPerRow) - 1} value={form.aisleAfter} onChange={update('aisleAfter')} /></label>
        <label className="field">Blocked seats (comma separated)<input value={form.blockedCodes} onChange={update('blockedCodes')} placeholder="B3, B4" /></label>
        <label className="field">Accessible seats (comma separated)<input value={form.accessibleCodes} onChange={update('accessibleCodes')} placeholder="A1, A2" /></label>
        <label className="check-label"><input type="checkbox" checked={form.identifiersConfirmed} onChange={e => setForm(previous => ({ ...previous, identifiersConfirmed: e.target.checked }))} /><span>Physical seat identifiers confirmed<span className="subtle">Permanent numbering or temporary labels must match this plan. Otherwise tickets stay general admission and private groups are disabled.</span></span></label>
        <p className="subtle">Maximum 300 seats. Saving adjusts event capacity to exclude blocked seats. Save your final layout before publishing.</p><button className="btn btn-primary" disabled={busy}>Save venue configuration</button>
      </form> : <p>Venue configuration is locked after publication. The saved version is used for every allocation and ticket.</p>}
      </section><section className="panel checkout-panel"><h2>Saved layout</h2>{data.layout ? <SeatMap layout={data.layout} /> : <p>No map saved. General admission can be published without one; assigned seating requires a saved layout.</p>}
        <div className="lifecycle-actions"><h2>Next step</h2>
          {draft && <><Link className="btn btn-ghost" to={`/organiser/events/${eventId}/edit`}>Edit event details</Link><button disabled={busy} className="btn btn-primary" onClick={() => action(() => setEventStatus(eventId, 'published'), 'Event published. Attendees can now book.')}>Publish saved event</button></>}
          {data.event.status === 'published' && !confirmClose && <button disabled={busy} className="btn btn-primary" onClick={() => setConfirmClose(true)}>Close registration…</button>}
          {confirmClose && <div role="alert"><p>Close registration for this event? This stops new bookings and cannot be undone in this demo.</p><button disabled={busy} className="btn btn-primary" onClick={() => action(() => setEventStatus(eventId, 'closed'), 'Registration closed. Ready for allocation.')}>Confirm close registration</button><button disabled={busy} className="btn btn-ghost" onClick={() => setConfirmClose(false)}>Keep open</button></div>}
          {data.event.status === 'closed' && !data.allocation && <>
            {data.event.seatIdentifiersConfirmed && <>
              <label className="field">Allocation preference<select disabled={busy} value={strategy} onChange={e => setStrategy(e.target.value)}>
                <option value="DEFAULT">Default</option><option value="PREFER_SAME_ROW">Prefer same row</option>
              </select></label>
              <p className="subtle">Both options first try to seat the complete group together. Default balances row subgroup sizes with a higher penalty for extra rows. Prefer same row keeps one row whenever feasible; otherwise it favours fewer rows with contiguous, evenly sized subgroups. Odd groups split as evenly as possible, and capacity, accessibility or fixed seats may limit balance.</p>
            </>}
            <button disabled={busy || !data.confirmedBookings} className="btn btn-primary" onClick={() => action(() => allocateSeats(eventId, data.event.seatIdentifiersConfirmed ? strategy : 'DEFAULT'), 'Allocation saved. Attendees can open their tickets now.')}>{busy ? 'Allocating…' : 'Run allocation'}</button>
          </>}
          {data.allocation && <><p>Allocation preference: {data.allocation.strategy === 'PREFER_SAME_ROW' ? 'Prefer same row' : data.allocation.strategy === 'DEFAULT' ? 'Default' : 'Not recorded'}</p><p className="form-banner success">{data.allocation.bookingCount} tickets ready · {data.allocation.splitGroupCount} split groups · recorded processing {data.allocation.elapsedMillis} ms</p><p className="subtle">Allocation is final for this layout. Repeated requests return the same saved run.</p><button disabled={busy} className="btn btn-primary" onClick={download}>Download anonymous CSV</button></>}
        </div>
      </section></div></>}
  </main></>;
}