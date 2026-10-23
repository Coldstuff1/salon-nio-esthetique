import type { Locale } from '@/i18n'
import { SALON_TIME_ZONE } from './dates'

const intlLocale: Record<Locale, string> = { fr: 'fr-CA', en: 'en-CA' }

/** Prices come from the backend in cents (`priceCents`, `costCents`): 6000 → "60,00 $" / "$60.00". */
export function formatPrice(cents: number, locale: Locale): string {
  const whole = cents % 100 === 0
  return new Intl.NumberFormat(intlLocale[locale], {
    style: 'currency',
    currency: 'CAD',
    currencyDisplay: 'narrowSymbol',
    minimumFractionDigits: whole ? 0 : 2,
  }).format(cents / 100)
}

/** "15 $ – 40 $" / "$15 – $40"; collapses to a single price when both ends match. */
export function formatPriceRange(minCents: number, maxCents: number, locale: Locale): string {
  if (minCents === maxCents) return formatPrice(minCents, locale)
  return `${formatPrice(minCents, locale)} – ${formatPrice(maxCents, locale)}`
}

// instants (`startsAt`, slots) are shown in the salon's time zone, wherever the visitor is

/** "jeudi 29 octobre 2026" / "Thursday, October 29, 2026" */
export function formatDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale[locale], { dateStyle: 'full', timeZone: SALON_TIME_ZONE }).format(
    new Date(iso),
  )
}

/** "09 h 30" / "9:30 a.m." */
export function formatTime(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale[locale], {
    timeStyle: 'short',
    timeZone: SALON_TIME_ZONE,
  }).format(new Date(iso))
}

/** A calendar day (`2026-10-29`, no time zone involved): "jeu. 29 oct." / "Thu, Oct 29" */
export function formatDay(day: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale[locale], {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(`${day}T00:00:00Z`))
}

/** 45 → "45 min", 90 → "1 h 30" / "1 h 30 min", 60 → "1 h". */
export function formatDuration(minutes: number, locale: Locale): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest} min`
  if (rest === 0) return `${hours} h`
  return locale === 'fr' ? `${hours} h ${String(rest).padStart(2, '0')}` : `${hours} h ${rest} min`
}

/** "30 – 45 min" style range; a single duration when both ends match. */
export function formatDurationRange(minMinutes: number, maxMinutes: number, locale: Locale): string {
  if (minMinutes === maxMinutes) return formatDuration(minMinutes, locale)
  return `${formatDuration(minMinutes, locale)} – ${formatDuration(maxMinutes, locale)}`
}

/** ISO weekday 1–7 (Monday first, as the backend's `dayOfWeek`) → "lundi" / "Monday". */
export function formatWeekday(isoDay: number, locale: Locale): string {
  // 2024-01-01 was a Monday
  return new Intl.DateTimeFormat(intlLocale[locale], { weekday: 'long', timeZone: 'UTC' }).format(
    new Date(Date.UTC(2024, 0, isoDay)),
  )
}

/** Cents → what a price field shows: 6000 → "60", 6050 → "60,50" / "60.50". `parsePrice` reads both back. */
export function centsToInput(cents: number, locale: Locale): string {
  const text = cents % 100 === 0 ? String(cents / 100) : (cents / 100).toFixed(2)
  return locale === 'fr' ? text.replace('.', ',') : text
}

/** What someone typed in a price field → cents; accepts "60", "60.5", "60,50", "60 $". Undefined if not a price. */
export function parsePrice(text: string): number | undefined {
  const cleaned = text.replace(/[\s$]/g, '').replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return undefined
  return Math.round(Number(cleaned) * 100)
}
