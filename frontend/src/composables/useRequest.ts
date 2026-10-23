import { ref, shallowRef, type Ref } from 'vue'
import { problemMessage } from '@/api/problem'

/** What an `api.GET(…)` / `api.POST(…)` call resolves to. */
interface ApiResult<T> {
  data?: T
  error?: unknown
  response: Response
}

/**
 * Runs an API call right away and tracks it for the template. Call `reload()` to run it again, e.g. when a
 * route param changes.
 *
 * ```ts
 * const { data: employees, loading, error } = useRequest(() => api.GET('/api/public/employees'))
 * ```
 */
export function useRequest<T>(call: () => Promise<ApiResult<T>>) {
  const data = shallowRef<T>() as Ref<T | undefined>
  const error = ref<string>()
  const status = ref<number>()
  const loading = ref(false)

  async function reload() {
    loading.value = true
    error.value = undefined
    try {
      const result = await call()
      status.value = result.response.status
      if (result.data !== undefined) {
        data.value = result.data
      } else {
        data.value = undefined
        error.value = problemMessage(result.error, result.response)
      }
    } catch (e) {
      // fetch itself failed: offline, server down
      data.value = undefined
      status.value = undefined
      error.value = problemMessage(e)
    } finally {
      loading.value = false
    }
  }

  void reload()
  return { data, error, status, loading, reload }
}
