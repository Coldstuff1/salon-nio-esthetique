/** Items grouped under their `category`, categories alphabetical, uncategorised ones last (`category: null`). */
export function groupByCategory<T extends { category?: string | null }>(
  items: readonly T[],
): { category: string | null; items: T[] }[] {
  const groups = new Map<string | null, T[]>()
  for (const item of items) {
    const key = item.category?.trim() || null
    groups.set(key, [...(groups.get(key) ?? []), item])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => (a === null ? 1 : b === null ? -1 : a.localeCompare(b)))
    .map(([category, items]) => ({ category, items }))
}
