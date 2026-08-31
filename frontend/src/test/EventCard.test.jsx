import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EventCard } from '../components/events/EventCard';

const baseEvent = {
  id: 1,
  title: 'Hackathon Kickoff',
  imageUrl: null,
  dateTime: new Date(Date.now() + 86400000).toISOString(),
  venue: 'Susan Wakil Building',
  city: 'Sydney',
  university: 'USYD',
  organiserName: 'Coding Society',
  organiserType: 'SOCIETY',
  category: 'Technology',
  tags: ['coding'],
};

describe('EventCard like button accessibility', () => {
  it('is unpressed with an accessible label when not liked', () => {
    render(
      <EventCard event={baseEvent} liked={false} likeCount={null} onToggleLike={() => {}} onOpenDetails={() => {}} />
    );
    const button = screen.getByRole('button', { name: 'Like Hackathon Kickoff' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('is pressed and relabelled when liked', () => {
    render(
      <EventCard event={baseEvent} liked={true} likeCount={3} onToggleLike={() => {}} onOpenDetails={() => {}} />
    );
    const button = screen.getByRole('button', { name: 'Unlike Hackathon Kickoff' });
    expect(button).toHaveAttribute('aria-pressed', 'true');
    expect(button).toHaveTextContent('3');
  });

  it('does not show a fake count when likeCount is null', () => {
    render(
      <EventCard event={baseEvent} liked={false} likeCount={null} onToggleLike={() => {}} onOpenDetails={() => {}} />
    );
    const button = screen.getByRole('button', { name: 'Like Hackathon Kickoff' });
    expect(button.textContent.replace(/[♥♡]/g, '').trim()).toBe('');
  });

  it('invokes the like toggle with the event', async () => {
    const user = userEvent.setup();
    const onToggleLike = vi.fn();
    render(
      <EventCard event={baseEvent} liked={false} likeCount={0} onToggleLike={onToggleLike} onOpenDetails={() => {}} />
    );
    await user.click(screen.getByRole('button', { name: 'Like Hackathon Kickoff' }));
    expect(onToggleLike).toHaveBeenCalledWith(baseEvent);
  });

  it('opens details from the View details action', async () => {
    const user = userEvent.setup();
    const onOpenDetails = vi.fn();
    render(
      <EventCard event={baseEvent} liked={false} likeCount={0} onToggleLike={() => {}} onOpenDetails={onOpenDetails} />
    );
    await user.click(screen.getByRole('button', { name: 'View details' }));
    expect(onOpenDetails).toHaveBeenCalledWith(baseEvent);
  });
});
