import { computed, ref } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'system'

// same key as the pre-paint script, public/theme-init.js
const STORAGE_KEY = 'nio.theme'
const systemDark = matchMedia('(prefers-color-scheme: dark)')

function readPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // storage blocked (private mode): fall back to the system setting
  }
  return 'system'
}

const preference = ref<ThemePreference>(readPreference())
const systemIsDark = ref(systemDark.matches)

const resolved = computed(() =>
  preference.value === 'system' ? (systemIsDark.value ? 'dark' : 'light') : preference.value,
)

function apply() {
  document.documentElement.dataset.theme = resolved.value
}

function setTheme(value: ThemePreference) {
  preference.value = value
  try {
    if (value === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // the choice still applies for this visit
  }
  apply()
}

systemDark.addEventListener('change', (event) => {
  systemIsDark.value = event.matches
  apply()
})

export function useTheme() {
  return { preference, resolved, setTheme }
}
