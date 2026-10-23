/** Shared look for text inputs, selects and textareas: hairline box on the surface color. */
export function fieldClasses(error?: string) {
  return [
    'w-full border bg-surface px-3.5 py-2.5 text-ink placeholder:text-ink-muted/70 transition-colors',
    'focus:outline-none focus-visible:outline-none focus:ring-2 focus:ring-blush',
    error ? 'border-danger focus:border-danger' : 'border-line-soft hover:border-line focus:border-accent',
  ]
}
