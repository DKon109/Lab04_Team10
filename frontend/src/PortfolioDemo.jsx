import { useState } from 'react';
import { SeatMap } from './components/SeatMap';

const KEY = 'tixora.portfolio.v1';
const initial = () => ({ status: 'draft', venue: '', capacity: 0, bookings: [], allocated: false });
const layout = { template: 'FRONT', source: 'EXTERNAL', version: 1, rows: 3, seatsPerRow: 6, aisleAfter: 3, seats: Array.from({ length: 18 }, (_, i) => ({ code: `${String.fromCharCode(65 + Math.floor(i / 6))}${i % 6 + 1}`, row: Math.floor(i / 6), column: i % 6 + 1, blocked: i === 17, accessible: i < 2 })) };

export default function PortfolioDemo() {
  const [state, setState] = useState(() => { try { return JSON.parse(localStorage.getItem(KEY)) || initial(); } catch { return initial(); } });
  const [role, setRole] = useState('organiser');
  const [notice, setNotice] = useState('');
  const [accessible, setAccessible] = useState(false);
  const [group, setGroup] = useState(false);
  const [selected, setSelected] = useState(null);
  function update(patch, message) { const next = { ...state, ...patch }; setState(next); localStorage.setItem(KEY, JSON.stringify(next)); setNotice(message); }
  function publish() {
    if (!state.venue.trim()) return setNotice('Publication rejected: add a venue before publishing.');
    if (state.capacity <= 0) return setNotice('Publication rejected: capacity must be positive.');
    if (state.status !== 'draft') return setNotice('Only a draft can be published.');
    update({ status: 'published' }, 'Published. Switch to Attendee to make a simulated booking.');
  }
  function checkout(outcome) {
    if (state.status !== 'published') return setNotice('Booking rejected: registration is not open.');
    if (outcome !== 'SUCCESS') return setNotice(`${outcome === 'DECLINED' ? 'Declined' : 'Cancelled'}: no booking created and no capacity used.`);
    if (state.bookings.length >= state.capacity) return setNotice('Booking rejected: this event is full.');
    const booking = { id: crypto.randomUUID(), ref: `DEMO-${String(state.bookings.length + 1).padStart(3, '0')}`, accessible, group: group ? 'Demo friends' : null };
    update({ bookings: [...state.bookings, booking] }, 'Mock checkout succeeded. No charge. Your ticket is awaiting assignment.'); setSelected(booking.id);
  }
  function allocate() {
    if (state.status !== 'closed') return setNotice('Close registration before allocating seats.');
    if (!state.bookings.length) return setNotice('Create at least one booking first.');
    if (state.allocated) return setNotice('The saved assignment is already final.');
    const available = layout.seats.filter(s => !s.blocked);
    const assignments = new Map();
    for (const b of [...state.bookings].sort((a,b) => Number(b.accessible) - Number(a.accessible))) {
      const index = available.findIndex(s => !b.accessible || s.accessible);
      if (index < 0) return setNotice('Assignment rejected: not enough accessible positions. Reset and try a smaller fixture.');
      assignments.set(b.id, available.splice(index, 1)[0].code);
    }
    update({ allocated: true, bookings: state.bookings.map(b => ({ ...b, seat: assignments.get(b.id) })) }, 'Illustrative assignment saved. Open Tickets to inspect the result.');
  }
  function download() {
    const rows = [['Booking reference', 'Group', 'Seat'], ...state.bookings.map(b => [b.ref, b.group ? 'G1' : '', b.seat || 'Pending'])];
    const url = URL.createObjectURL(new Blob([rows.map(r => r.join(',')).join('\n')], { type: 'text/csv' }));
    const a = document.createElement('a'); a.href = url; a.download = 'tixora-demo-allocation.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  const ticket = state.bookings.find(b => b.id === selected) || state.bookings[0];
  return <>
    <header className="portfolio-nav"><a href="#" className="portfolio-brand">Tixora<span>INTERACTIVE PORTFOLIO</span></a><a className="btn btn-ghost" href="https://github.com/DKon109/Lab04_Team10">View source ↗</a></header>
    <main className="page">
      <section className="portfolio-intro"><p className="eyebrow">EVENTS, FROM FIRST IDEA TO FINAL SEAT</p><h1>A place for<br/><em>everyone.</em></h1><p>Explore the event lifecycle I helped build: validate a venue, publish an event, close registration and inspect saved tickets.</p><p className="subtle">Ryoji Kondo · Java / Spring Boot · React · MySQL · Team project</p></section>
      <section className="panel portfolio-guide"><strong>Always-available browser demo</strong><p>This public preview uses fictional data stored only in your browser. Login, server security, MySQL transactions and the team's allocation algorithm are not running here. The full backend and its tests are available in the repository. Do not enter personal information.</p><div className="portfolio-steps"><span>01 Configure & publish</span><span>02 Make a booking</span><span>03 Close & assign</span><span>04 Inspect tickets</span></div></section>
      <div className="portfolio-toolbar"><div className="segmented" role="tablist" aria-label="Demo workspace">{[['organiser','Organiser'],['attendee','Attendee'],['tickets','Tickets']].map(([value,label]) => <button key={value} role="tab" aria-selected={role === value} onClick={() => { setRole(value); setNotice(''); }}>{label}</button>)}</div><button className="btn btn-ghost" onClick={() => { localStorage.removeItem(KEY); setState(initial()); setSelected(null); setNotice('Demo reset. Start with the organiser workspace.'); }}>Reset demo</button></div>
      {notice && <p className="form-banner success" role="status">{notice}</p>}
      <div className="management-stats"><div><small>EVENT STATUS</small><strong>{state.status}</strong></div><div><small>BOOKINGS</small><strong>{state.bookings.length} / {state.capacity}</strong></div><div><small>ASSIGNMENT</small><strong>{state.allocated ? 'Saved' : 'Pending'}</strong></div></div>
      <div className="checkout-layout"><section className="panel checkout-panel">
        <p className="eyebrow">FICTIONAL DEMO EVENT</p><h2>Campus creative evening</h2><p>Society performances, good company, and a shared front-stage seating plan.</p>
        {role === 'organiser' && <><h3>Configure the venue</h3><p>Try publishing before configuring a venue to see a validation failure. Then apply the template and publish again.</p><label className="field">Venue name<input value={state.venue} disabled={state.status !== 'draft'} onChange={e => update({ venue: e.target.value }, '')}/></label><label className="field">Usable capacity<input type="number" value={state.capacity} disabled={state.status !== 'draft'} onChange={e => update({ capacity: Math.max(0, Math.min(17, Number(e.target.value))) }, '')}/></label><p className="subtle">Demo template: 18 positions, one blocked, two illustrative accessible positions; maximum usable capacity 17.</p><div className="portfolio-actions"><button className="btn btn-ghost" disabled={state.status !== 'draft'} onClick={() => update({ venue: 'Demo Studio · illustrative layout', capacity: 17 }, 'Template applied. The layout is illustrative, not a verified physical venue.')}>Apply demo venue</button><button className="btn btn-primary" disabled={state.status !== 'draft'} onClick={publish}>Publish event</button><button className="btn btn-ghost" disabled={state.status !== 'published'} onClick={() => update({ status: 'closed' }, 'Registration closed. Existing bookings are preserved; new bookings are rejected.')}>Close registration</button><button className="btn btn-primary" disabled={state.status !== 'closed'} onClick={allocate}>Run illustrative assignment</button></div><p className="subtle">This small preview assigns accessible bookings first, then the next available seat. It does not reproduce the backend's group optimisation or performance benchmarks.</p></>}
        {role === 'attendee' && <><h3>Reserve a place</h3><p>Use the organiser tab to publish first. Each successful action below creates one fictional attendee, so you can explore a small group.</p><label className="check-label"><input type="checkbox" checked={group} onChange={e => setGroup(e.target.checked)}/>Join the demo friends group</label><label className="check-label"><input type="checkbox" checked={accessible} onChange={e => setAccessible(e.target.checked)}/>Request an accessible position</label><div className="portfolio-actions"><button className="btn btn-primary" onClick={() => checkout('SUCCESS')}>Simulate successful payment</button><button className="btn btn-ghost" onClick={() => checkout('DECLINED')}>Simulate decline</button><button className="btn btn-ghost" onClick={() => checkout('CANCELLED')}>Cancel checkout</button></div><p className="subtle">No card details, real payments or reservations. After closing, try booking again to see the lifecycle restriction.</p></>}
        {role === 'tickets' && <><h3>Saved demo tickets</h3>{!ticket ? <p>No bookings yet. Publish the event and use the Attendee workspace.</p> : <><label className="field">Fictional booking<select value={ticket.id} onChange={e => setSelected(e.target.value)}>{state.bookings.map(b => <option key={b.id} value={b.id}>{b.ref}</option>)}</select></label><p className="reference">{ticket.ref}</p><p className="eyebrow">{ticket.seat ? 'ASSIGNED SEAT' : 'AWAITING ASSIGNMENT'}</p><p className="assigned-seat">{ticket.seat || 'Pending'}</p><p>{ticket.group || 'Individual booking'}{ticket.accessible && ' · Accessible position requested'}</p><p className="subtle">The full application restricts tickets to their owner. This browser preview deliberately lets you inspect all fictional fixtures.</p>{state.allocated && <button className="btn btn-primary" onClick={download}>Download anonymous CSV</button>}</>}</>}
      </section><section className="panel checkout-panel"><h2>{role === 'tickets' && ticket?.seat ? `Your seat: ${ticket.seat}` : 'The saved venue layout'}</h2><SeatMap layout={layout} selectedCode={role === 'tickets' ? ticket?.seat : undefined}/><p className="subtle">Illustrative geometry and accessibility positions. Not a verified USYD seating plan.</p></section></div>
      <section className="panel portfolio-guide"><p className="eyebrow">MY CONTRIBUTION</p><h2>Business rules backed by evidence.</h2><p>I implemented event ownership and lifecycle rules, venue template generation and validation, and integration tests covering persistence and concurrent registration closure. Teammates owned the allocation algorithm, booking/payment modules and the main frontend workflows.</p><a href="https://github.com/DKon109/Lab04_Team10#my-contribution--ryoji-kondo">Read the implementation & test evidence ↗</a></section>
    </main><footer className="page subtle">Tixora · University of Sydney team project · Portfolio preview by Ryoji Kondo</footer>
  </>;
}
