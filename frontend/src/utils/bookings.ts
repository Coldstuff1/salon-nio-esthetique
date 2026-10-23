import type { Schemas } from '@/api/http'

type Booking = Schemas['BookingView']

/** Still to come and still on: the client can cancel it (the backend refuses once it has started). */
export function isUpcoming(booking: Booking, now = Date.now()): boolean {
  return booking.status === 'RESERVED' && Date.parse(booking.startsAt) > now
}

/** The booking page pre-filled with the same employee and treatments, ready to pick a new time. */
export function bookAgainLink(booking: Booking) {
  return {
    name: 'booking',
    query: {
      employee: booking.employeeId,
      services: booking.services.map((s) => s.serviceTypeId).join(','),
      step: 'time',
    },
  }
}
