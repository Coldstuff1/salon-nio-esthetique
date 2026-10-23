import type { Locale } from '@/i18n'

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

export function formatDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale[locale], { dateStyle: 'long' }).format(new Date(iso))
}

export function formatTime(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale[locale], { timeStyle: 'short' }).format(new Date(iso))
}
