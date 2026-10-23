import { createI18n, useI18n } from 'vue-i18n'
import { computed } from 'vue'
import type { Schemas } from '@/api/http'
import fr from './fr.json'
import en from './en.json'

export type Locale = 'fr' | 'en'
export const locales: Locale[] = ['fr', 'en']

const STORAGE_KEY = 'nio.locale'

function initialLocale(): Locale {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'fr' || saved === 'en') return saved
  } catch {
    // storage blocked: fall through to the browser language
  }
  return navigator.language.toLowerCase().startsWith('en') ? 'en' : 'fr'
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'fr',
  messages: { fr, en },
})

document.documentElement.lang = i18n.global.locale.value

/** Catalog text from the backend: English always, French when an admin has entered it. */
export type LocalizedText = Schemas['LocalizedText']

/**
 * Current locale, a setter that persists it and keeps `<html lang>` in sync, and `localize` to pick the
 * current locale's side of catalog text (French falls back to English; absent text stays `null`).
 */
export function useLocale() {
  const { locale } = useI18n()
  return {
    locale: computed(() => locale.value as Locale),
    localize(text?: LocalizedText | null): string | null {
      if (!text) return null
      return (locale.value === 'fr' && text.fr?.trim()) || text.en
    },
    setLocale(value: Locale) {
      locale.value = value
      document.documentElement.lang = value
      try {
        localStorage.setItem(STORAGE_KEY, value)
      } catch {
        // the choice still applies for this visit
      }
    },
  }
}
