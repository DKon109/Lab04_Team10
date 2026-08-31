// Australian-style date formatting, e.g. "Fri 12 Sep, 5:00 pm".
const DATE_FORMAT = new Intl.DateTimeFormat('en-AU', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});
const TIME_FORMAT = new Intl.DateTimeFormat('en-AU', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});

export function formatEventDate(dateTime) {
  const date = new Date(dateTime);
  if (Number.isNaN(date.getTime())) return '';
  return `${DATE_FORMAT.format(date)}, ${TIME_FORMAT.format(date)}`;
}

export function isPast(dateTime) {
  return new Date(dateTime) < new Date();
}
