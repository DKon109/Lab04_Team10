import { fireEvent, render, screen, cleanup } from '@testing-library/react';
import { afterEach, beforeEach, expect, test } from 'vitest';
import PortfolioDemo from '../PortfolioDemo';
beforeEach(() => { localStorage.clear(); });
afterEach(cleanup);
const click = name => fireEvent.click(screen.getByRole('button', { name, exact: true }));
const tab = name => fireEvent.click(screen.getByRole('tab', { name, exact: true }));
function publish() { click('Apply demo venue'); click('Publish event'); }
test('shows the browser-only boundary and rejects incomplete publication', () => {
  render(<PortfolioDemo/>);
  expect(screen.getByText(/server security, MySQL transactions/)).toBeInTheDocument();
  click('Publish event');
  expect(screen.getByRole('status')).toHaveTextContent('add a venue');
});
test('preserves confirmed booking through closure and saves a highlighted accessible ticket', () => {
  render(<PortfolioDemo/>); publish(); tab('Attendee');
  fireEvent.click(screen.getByLabelText('Request an accessible position'));
  click('Simulate decline');
  expect(JSON.parse(localStorage.getItem('tixora.portfolio.v1')).bookings).toHaveLength(0);
  click('Simulate successful payment'); tab('Organiser'); click('Close registration');
  tab('Attendee'); click('Simulate successful payment');
  expect(screen.getByRole('status')).toHaveTextContent('not open');
  tab('Organiser'); click('Run illustrative assignment'); tab('Tickets');
  expect(screen.getByRole('img')).toHaveAttribute('aria-label', expect.stringContaining('Your seat is A1'));
  const saved = JSON.parse(localStorage.getItem('tixora.portfolio.v1'));
  expect(saved.bookings).toHaveLength(1); expect(saved.bookings[0].seat).toBe('A1');
});
test('restores the fixture after remount and resets only the demo state', () => {
  const view = render(<PortfolioDemo/>); publish(); view.unmount();
  localStorage.setItem('unrelated', 'keep'); render(<PortfolioDemo/>);
  expect(screen.getByText('published')).toBeInTheDocument();
  click('Reset demo'); expect(screen.getByText('draft')).toBeInTheDocument();
  expect(localStorage.getItem('unrelated')).toBe('keep');
});
