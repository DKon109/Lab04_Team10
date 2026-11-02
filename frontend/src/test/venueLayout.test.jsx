import { describe, it, expect } from 'vitest';
import {
  buildRoomLayout,
  buildTemplateLayout,
} from '../components/VenuePicker';

const ROOM = {
  id: 'woolley',
  name: 'Woolley Lecture Theatre',
  building: 'Quadrangle',
  capacity: 350,
  rows: 14,
  seatsPerRow: 25,
  hasNumberedSeats: true,
  shape: 'front',
};

const TEMPLATE = {
  id: 'fan',
  name: 'Fan / semicircle',
  blurb: 'Curved rows around a stage at the front.',
  shape: 'fan',
};

describe('buildRoomLayout', () => {
  it('produces a usyd-shaped layout', () => {
    const layout = buildRoomLayout(ROOM);
    expect(layout.mode).toBe('usyd');
    expect(layout.roomId).toBe('woolley');
    expect(layout.roomName).toBe('Woolley Lecture Theatre');
    expect(layout.capacity).toBe(350);
    expect(layout.shape).toBe('front');
    expect(layout.rows).toBe(14);
    expect(layout.seatsPerRow).toBe(25);
    expect(layout.hasNumberedSeats).toBe(true);
  });

  it('preserves hasNumberedSeats: false', () => {
    const layout = buildRoomLayout({ ...ROOM, hasNumberedSeats: false });
    expect(layout.hasNumberedSeats).toBe(false);
  });
});

describe('buildTemplateLayout', () => {
  it('rejects oversized or fractional dimensions rather than keeping a stale valid selection', () => {
    expect(buildTemplateLayout(TEMPLATE, { rows: 20, seatsPerRow: 20 })).toBeNull();
    expect(buildTemplateLayout(TEMPLATE, { rows: 2.5, seatsPerRow: 10 })).toBeNull();
    expect(buildTemplateLayout(TEMPLATE, { rows: 21, seatsPerRow: 1 })).toBeNull();
  });
  it('produces an external-shaped layout with computed capacity', () => {
    const layout = buildTemplateLayout(TEMPLATE, {
      rows: 8,
      seatsPerRow: 12,
      hasNumberedSeats: true,
    });
    expect(layout.mode).toBe('external');
    expect(layout.templateId).toBe('fan');
    expect(layout.templateName).toBe('Fan / semicircle');
    expect(layout.capacity).toBe(96);
    expect(layout.shape).toBe('fan');
    expect(layout.rows).toBe(8);
    expect(layout.seatsPerRow).toBe(12);
    expect(layout.hasNumberedSeats).toBe(true);
  });

  it('returns null when capacity would be zero or negative', () => {
    expect(
      buildTemplateLayout(TEMPLATE, { rows: 0, seatsPerRow: 10, hasNumberedSeats: true })
    ).toBeNull();
    expect(
      buildTemplateLayout(TEMPLATE, { rows: 10, seatsPerRow: 0, hasNumberedSeats: true })
    ).toBeNull();
    expect(
      buildTemplateLayout(TEMPLATE, { rows: -1, seatsPerRow: 10, hasNumberedSeats: true })
    ).toBeNull();
  });

  it('carries through hasNumberedSeats: false', () => {
    const layout = buildTemplateLayout(TEMPLATE, {
      rows: 5,
      seatsPerRow: 10,
      hasNumberedSeats: false,
    });
    expect(layout.hasNumberedSeats).toBe(false);
  });
});
