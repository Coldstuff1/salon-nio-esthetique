/** Where the salon is; employees' hours and bookable days are in this zone (backend `salon.zone`). */
export const SALON_TIME_ZONE = 'America/Toronto'

/** Today in the salon, as `YYYY-MM-DD`. */
export function salonToday(): string {
  // en-CA formats dates as YYYY-MM-DD
  return new Intl.DateTimeFormat('en-CA', { timeZone: SALON_TIME_ZONE }).format(new Date())
}

/** The salon calendar day an instant falls on, as `YYYY-MM-DD`. */
export function salonDay(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: SALON_TIME_ZONE }).format(new Date(iso))
}

/** `YYYY-MM-DD` plus some days, calendar arithmetic only. */
export function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

/** Minutes the salon's clock is ahead of UTC at an instant (negative in Canada, e.g. -240 in summer). */
function salonOffsetMinutes(at: number): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: SALON_TIME_ZONE,
    hourCycle: 'h23',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric',
  }).formatToParts(at)
  const part = (type: string) => Number(parts.find((p) => p.type === type)?.value)
  const asUtc = Date.UTC(
    part('year'),
    part('month') - 1,
    part('day'),
    part('hour'),
    part('minute'),
    part('second'),
  )
  return Math.round((asUtc - Math.floor(at / 1000) * 1000) / 60_000)
}

/** The instant (ISO) of a salon wall-clock time, e.g. `2026-11-02` + `09:30` in Toronto, across DST changes. */
export function salonInstant(day: string, time: string): string {
  const wallClock = Date.parse(`${day}T${time.length === 5 ? `${time}:00` : time}Z`)
  // the offset at the guessed instant, then once more in case the guess fell across a DST change
  let instant = wallClock - salonOffsetMinutes(wallClock) * 60_000
  instant = wallClock - salonOffsetMinutes(instant) * 60_000
  return new Date(instant).toISOString()
}

/** ISO weekday of a calendar day: 1 = Monday … 7 = Sunday, as the backend's `dayOfWeek`. */
export function isoWeekday(day: string): number {
  return new Date(`${day}T00:00:00Z`).getUTCDay() || 7
}

/** The salon wall-clock time of an instant, `HH:mm` (24 h). */
export function salonTimeOfDay(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: SALON_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(iso))
}
