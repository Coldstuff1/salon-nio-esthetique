import { reactive, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import { useLocale } from '@/i18n'
import { centsToInput, parsePrice } from '@/utils/format'

type StaffProfile = Schemas['StaffProfile']

/**
 * An offering as the editor sees it: a price, one more whole number (duration or stock), for products what a unit
 * cost the employee, and for treatments where it sits in their list (null: alphabetically, after numbered ones)
 * and how many can be booked per day (null: no limit).
 */
export interface Offering {
  priceCents: number
  costCents?: number
  amount: number
  active: boolean
  displayOrder?: number | null
  dailyLimit?: number | null
}

/** The highest list position a treatment can be given. */
const MAX_ORDER = 999
/** The highest daily limit a treatment can be given. */
const MAX_LIMIT = 99

interface Row {
  offered: boolean
  price: string
  cost: string
  amount: string
  /** list position; blank for none (a number input's v-model may hand back a number) */
  order: string
  /** bookings allowed per day; blank for no limit */
  limit: string
  /** what was last saved, to know if the row changed */
  saved: string
  saving: boolean
  error?: string
}

/**
 * Editable rows for the staff treatment and product pages, one per catalog item id. Each row is saved on its
 * own; the server's updated profile replaces `profile`.
 */
export function useOfferingRows(options: {
  profile: Ref<StaffProfile | undefined>
  /** ids of the catalog items listed on the page */
  ids: () => string[]
  /** the employee's current offerings, by catalog item id */
  current: (profile: StaffProfile) => Map<string, Offering>
  /** the amount a new offering starts with */
  defaultAmount: number
  minAmount: number
  amountError: string
  /** products also record their cost */
  withCost?: boolean
  /** treatments also take an optional list position */
  withOrder?: boolean
  /** treatments also take an optional daily limit */
  withLimit?: boolean
  save: (
    id: string,
    offering: Offering,
  ) => Promise<{ data?: StaffProfile; error?: unknown; response: Response }>
}) {
  const { t } = useI18n()
  const { locale } = useLocale()
  const rows = reactive<Record<string, Row>>({})
  const snapshot = (row: Pick<Row, 'offered' | 'price' | 'cost' | 'amount' | 'order' | 'limit'>) =>
    `${row.offered}|${row.price}|${row.cost}|${row.amount}|${row.order}|${row.limit}`

  /** Takes the server's values, leaving rows with unsaved changes alone unless `only` names one. */
  function reset(profile: StaffProfile, only?: string) {
    const current = options.current(profile)
    for (const [id, offering] of current) {
      if (only ? id !== only : rows[id] && snapshot(rows[id]) !== rows[id].saved) continue
      const values = {
        offered: offering.active,
        price: centsToInput(offering.priceCents, locale.value),
        cost: offering.costCents === undefined ? '' : centsToInput(offering.costCents, locale.value),
        amount: String(offering.amount),
        order: offering.displayOrder == null ? '' : String(offering.displayOrder),
        limit: offering.dailyLimit == null ? '' : String(offering.dailyLimit),
      }
      rows[id] = { ...values, saved: snapshot(values), saving: false }
    }
  }
  watch(options.profile, (profile) => profile && reset(profile), { immediate: true })
  // items the employee doesn't offer yet start blank and unticked
  watch(
    options.ids,
    (ids) => {
      for (const id of ids) {
        if (rows[id]) continue
        const values = {
          offered: false,
          price: '',
          cost: '',
          amount: String(options.defaultAmount),
          order: '',
          limit: '',
        }
        rows[id] = { ...values, saved: snapshot(values), saving: false }
      }
    },
    { immediate: true },
  )

  const row = (id: string): Row => rows[id]!

  const dirty = (id: string) => snapshot(row(id)) !== row(id).saved
  const priceError = (id: string) =>
    dirty(id) && parsePrice(row(id).price) === undefined ? t('staff.offerings.priceInvalid') : undefined
  const costError = (id: string) =>
    options.withCost && dirty(id) && parsePrice(row(id).cost) === undefined
      ? t('staff.offerings.priceInvalid')
      : undefined
  const amountError = (id: string) => {
    const value = Number(row(id).amount)
    return dirty(id) && !(Number.isInteger(value) && value >= options.minAmount && row(id).amount !== '')
      ? options.amountError
      : undefined
  }

  /** Whether an optional whole-number field is blank or within 1 to `max`. */
  const blankOrUpTo = (input: string, max: number) => {
    const text = String(input).trim()
    const value = Number(text)
    return text === '' || (Number.isInteger(value) && value >= 1 && value <= max)
  }
  const orderError = (id: string) =>
    options.withOrder && dirty(id) && !blankOrUpTo(row(id).order, MAX_ORDER)
      ? t('staff.offerings.orderInvalid')
      : undefined
  const limitError = (id: string) =>
    options.withLimit && dirty(id) && !blankOrUpTo(row(id).limit, MAX_LIMIT)
      ? t('staff.offerings.limitInvalid')
      : undefined
  /** An optional whole-number field as sent: null when blank. */
  const optionalNumber = (enabled: boolean | undefined, input: string) =>
    enabled && String(input).trim() !== '' ? Number(input) : null

  async function save(id: string) {
    const current = row(id)
    const priceCents = parsePrice(current.price)
    const costCents = options.withCost ? parsePrice(current.cost) : undefined
    if (priceCents === undefined || amountError(id) || costError(id) || orderError(id) || limitError(id))
      return
    current.saving = true
    current.error = undefined
    try {
      const { data, error, response } = await options.save(id, {
        priceCents,
        costCents,
        amount: Number(current.amount),
        active: current.offered,
        displayOrder: optionalNumber(options.withOrder, current.order),
        dailyLimit: optionalNumber(options.withLimit, current.limit),
      })
      if (data) {
        reset(data, id)
        options.profile.value = data
      } else {
        current.error = problemMessage(error, response)
      }
    } catch (e) {
      current.error = problemMessage(e)
    } finally {
      current.saving = false
    }
  }

  return { row, dirty, priceError, costError, amountError, orderError, limitError, save }
}
