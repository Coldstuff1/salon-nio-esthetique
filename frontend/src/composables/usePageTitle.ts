import { onUnmounted, readonly, ref, watchEffect } from 'vue'

const override = ref<string>()

/** The current page's own title, if it set one; `App.vue` falls back to the route's `meta.title`. */
export const pageTitle = readonly(override)

/**
 * Names the browser tab after what the page shows (e.g. an employee's name) instead of its route's
 * `meta.title`, for as long as the calling component is mounted. Return undefined to use the route's title.
 */
export function usePageTitle(title: () => string | undefined) {
  watchEffect(() => (override.value = title()))
  onUnmounted(() => (override.value = undefined))
}
