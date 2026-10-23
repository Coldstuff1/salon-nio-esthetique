import { computed } from 'vue'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import type { Schemas } from '@/api/http'

export const bookingSteps = ['employee', 'services', 'time', 'review'] as const
export type BookingStep = (typeof bookingSteps)[number]

type ProductSelection = Schemas['ProductSelection']

/** What the booking page's URL can carry; see `useBookingDraft`. */
export interface DraftChanges {
  employee?: string
  services?: string[]
  products?: ProductSelection[]
  /** salon calendar day being browsed, `YYYY-MM-DD` */
  day?: string
  /** chosen start, an ISO instant from the slots endpoint */
  time?: string
}

/**
 * The booking in progress, kept in the URL query so it survives the Keycloak login round trip, works with
 * the back button, and can be linked to (`/book?services=…`, `/book?employee=…`):
 * `?employee=<id>&services=<id>,<id>&products=<id>:<qty>&day=2026-10-29&time=<instant>&step=time`.
 *
 * The step comes from `step`, but never past what the draft allows (no services, no time step).
 */
export function useBookingDraft() {
  const route = useRoute()
  const router = useRouter()

  const param = (key: string) => {
    const value = route.query[key]
    return typeof value === 'string' && value ? value : undefined
  }

  const employeeId = computed(() => param('employee'))
  const serviceIds = computed(() => param('services')?.split(',').filter(Boolean) ?? [])
  const products = computed<ProductSelection[]>(() =>
    (param('products')?.split(',') ?? [])
      .map((entry) => {
        const [productId = '', quantity] = entry.split(':')
        return { productId, quantity: Number(quantity) }
      })
      .filter((p) => p.productId && Number.isInteger(p.quantity) && p.quantity > 0),
  )
  const day = computed(() => param('day'))
  const startsAt = computed(() => param('time'))

  /** furthest step the draft supports */
  const reachable = computed(() => {
    if (!employeeId.value) return 0
    if (!serviceIds.value.length) return 1
    if (!startsAt.value) return 2
    return 3
  })
  const step = computed<BookingStep>(() => {
    const requested = bookingSteps.indexOf(param('step') as BookingStep)
    // with no step asked for, open on the services step at most, so a pre-filled draft gets a look-over
    const index = requested >= 0 ? requested : Math.min(reachable.value, 1)
    return bookingSteps[Math.min(index, reachable.value)]!
  })

  function query(changes: DraftChanges, nextStep?: BookingStep): LocationQuery {
    const next = {
      employee: employeeId.value,
      services: serviceIds.value,
      products: products.value,
      day: day.value,
      time: startsAt.value,
      ...changes,
    }
    // a different employee or set of services means different hours and durations
    if (('employee' in changes && changes.employee !== employeeId.value) || 'services' in changes) {
      if (!('time' in changes)) next.time = undefined
    }
    if ('employee' in changes && changes.employee !== employeeId.value && !('day' in changes))
      next.day = undefined
    const result: Record<string, string> = {}
    if (next.employee) result.employee = next.employee
    if (next.services?.length) result.services = next.services.join(',')
    if (next.products?.length)
      result.products = next.products.map((p) => `${p.productId}:${p.quantity}`).join(',')
    if (next.day) result.day = next.day
    if (next.time) result.time = next.time
    result.step = nextStep ?? step.value
    return result
  }

  return {
    employeeId,
    serviceIds,
    products,
    day,
    startsAt,
    step,
    /** Changes the draft in place (no new history entry). */
    update: (changes: DraftChanges) => router.replace({ query: query(changes) }),
    /** Moves to a step, optionally changing the draft; the back button returns to the current step. */
    goTo: (nextStep: BookingStep, changes: DraftChanges = {}) =>
      router.push({ query: query(changes, nextStep) }),
  }
}
