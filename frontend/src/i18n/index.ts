import { createI18n, useI18n } from 'vue-i18n'
import { computed } from 'vue'
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

/** Current locale plus a setter that persists it and keeps `<html lang>` in sync. */
export function useLocale() {
  const { locale } = useI18n()
  return {
    locale: computed(() => locale.value as Locale),
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
