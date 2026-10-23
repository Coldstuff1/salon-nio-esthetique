import { i18n } from '@/i18n'
import type { Schemas } from './http'

/**
 * Text for a `BaseAlert` after a failed call. Rule violations (400, 409) carry the backend's own explanation,
 * e.g. "That time slot is already booked"; anything else gets a translated message for its status.
 *
 * ```ts
 * const { data, error, response } = await api.POST('/api/bookings', { body })
 * if (error) message.value = problemMessage(error, response)
 * ```
 */
export function problemMessage(error: unknown, response?: Response): string {
  const { t } = i18n.global
  const status = response?.status ?? (error as Schemas['ProblemDetail'] | undefined)?.status
  const detail = (error as Schemas['ProblemDetail'] | undefined)?.detail

  if ((status === 400 || status === 409) && detail) return detail
  switch (status) {
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
