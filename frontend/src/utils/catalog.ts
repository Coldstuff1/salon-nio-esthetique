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

/**
 * An employee's treatments in the order they chose: numbered ones first, lowest number first, then the rest by
 * name in the current language (ties on a number also go by name). Pass `useLocale().localize`.
 */
export function byDisplayOrder<T extends { name: LocalizedText; displayOrder?: number | null }>(
  items: readonly T[],
  localize: (text: LocalizedText) => string | null,
): T[] {
  // the sort is stable, so items with the same number (or none) keep their alphabetical order
  const rank = (item: T) => item.displayOrder ?? Number.MAX_SAFE_INTEGER
  return byName(items, localize).sort((a, b) => rank(a) - rank(b))
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

/** A sub-treatment names the treatments it can be booked with; a top-level one names none. */
type WithParents = { serviceTypeId: string; parentIds: readonly string[] }

/** The selection without the sub-treatments none of whose parents are still selected. */
export function withoutOrphans(ids: readonly string[], services: readonly WithParents[]): string[] {
  const parentsOf = new Map(services.map((s) => [s.serviceTypeId, s.parentIds]))
  return ids.filter((id) => {
    const parents = parentsOf.get(id) ?? []
    return !parents.length || parents.some((p) => ids.includes(p))
  })
}

/**
 * Top-level treatments in the order given, each with the sub-treatments to show under it: those of a selected
 * parent, each under the first selected parent it belongs to so it's listed once. Unselected parents get none.
 */
export function nestSubTreatments<T extends WithParents>(
  services: readonly T[],
  selectedIds: readonly string[],
): { service: T; children: T[] }[] {
  const parents = services.filter((s) => !s.parentIds.length)
  const placed = new Set<string>()
  return parents.map((service) => {
    if (!selectedIds.includes(service.serviceTypeId)) return { service, children: [] }
    const children = services.filter(
      (s) => s.parentIds.includes(service.serviceTypeId) && !placed.has(s.serviceTypeId),
    )
    children.forEach((c) => placed.add(c.serviceTypeId))
    return { service, children }
  })
}
