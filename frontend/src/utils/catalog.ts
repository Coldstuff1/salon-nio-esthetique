import type { LocalizedText } from '@/i18n'

/**
 * Items grouped under the category `categoryOf` gives them (usually their localized `category`), categories
 * alphabetical, uncategorised ones last (`category: null`). Items keep their order within a group.
 */
export function groupByCategory<T>(
  items: readonly T[],
  categoryOf: (item: T) => string | null | undefined,
): { category: string | null; items: T[] }[] {
  const groups = new Map<string | null, T[]>()
  for (const item of items) {
    const key = categoryOf(item)?.trim() || null
    groups.set(key, [...(groups.get(key) ?? []), item])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => (a === null ? 1 : b === null ? -1 : a.localeCompare(b)))
    .map(([category, items]) => ({ category, items }))
}

/**
 * Catalog items sorted by their name in the current language; the backend sorts by the English name, so lists
 * re-sort here when the visitor reads in French. Pass `useLocale().localize`.
 */
export function byName<T extends { name: LocalizedText }>(
  items: readonly T[],
  localize: (text: LocalizedText) => string | null,
): T[] {
  return [...items].sort((a, b) => localize(a.name)!.localeCompare(localize(b.name)!))
}

/** Catalog text as the two inputs of an admin form, English and French. */
export const toInputs = (text?: LocalizedText | null) => ({
  en: text?.en ?? '',
  fr: text?.fr ?? '',
})

/** The two inputs back into catalog text, left out when both are blank; a blank French reads as English. */
export function fromInputs(inputs: { en: string; fr: string }): LocalizedText | undefined {
  const en = inputs.en.trim()
  const fr = inputs.fr.trim()
  return en || fr ? { en, fr: fr || null } : undefined
}
