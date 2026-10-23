import { i18n } from '@/i18n'
import type { Schemas } from './http'

/**
 * The backend's error body: a ProblemDetail whose `code` (e.g. `slot-taken`, see the backend's `ErrorCode`)
 * says what went wrong, `params` fill the message, and `fields` name the inputs that failed validation.
 * Its English `detail` is for developers, never shown.
 */
export type Problem = Schemas['ProblemDetail'] & {
  code?: string
  params?: Record<string, unknown>
  fields?: string[]
}

const asProblem = (error: unknown): Problem | undefined =>
  error && typeof error === 'object' ? (error as Problem) : undefined

/** The error's code, to react to specific failures (e.g. `out-of-stock`). */
export function problemCode(error: unknown): string | undefined {
  return asProblem(error)?.code
}

/** Inputs the backend rejected, by request field name (e.g. `phone`). */
export function problemFields(error: unknown): string[] {
  return asProblem(error)?.fields ?? []
}

/**
 * Text for a `BaseAlert` after a failed call, in the visitor's language: the translation of the error's code
 * (`errors.codes.<code>`), else a message for its status, else a network error.
 *
 * ```ts
 * const { data, error, response } = await api.POST('/api/bookings', { body })
 * if (error) message.value = problemMessage(error, response)
 * ```
 */
export function problemMessage(error: unknown, response?: Response): string {
  const { t, te } = i18n.global
  const problem = asProblem(error)
  const key = `errors.codes.${problem?.code}`
  if (problem?.code && te(key)) return t(key, (problem.params ?? {}) as Record<string, unknown>)

  switch (response?.status ?? problem?.status) {
    case 400:
      return t('errors.invalid')
    case 401:
    case 403:
      return t('errors.notAllowed')
    case 404:
      return t('errors.missing')
    case 409:
      return t('errors.conflict')
  }
  return response ? t('errors.generic') : t('errors.network')
}
