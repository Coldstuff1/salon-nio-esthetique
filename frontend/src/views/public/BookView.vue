<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api, type Schemas } from '@/api/http'
import { problemCode, problemFields, problemMessage } from '@/api/problem'
import { useAuth } from '@/auth/keycloak'
import EmployeePicker from '@/components/booking/EmployeePicker.vue'
import QuoteSummary from '@/components/booking/QuoteSummary.vue'
import ServicePicker from '@/components/booking/ServicePicker.vue'
import SlotPicker from '@/components/booking/SlotPicker.vue'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import RequestState from '@/components/ui/RequestState.vue'
import {
  bookingSteps,
  useBookingDraft,
  type BookingStep,
  type DraftChanges,
} from '@/composables/useBookingDraft'
import { useRequest } from '@/composables/useRequest'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const { authenticated, hasRole, login } = useAuth()
const draft = useBookingDraft()
const { employeeId, serviceIds, products, day, startsAt, step } = draft

// step 1: everyone, narrowed to who offers all the treatments a link pre-selected (/book?services=…)
const employees = useRequest(() => api.GET('/api/public/employees'))
const services = useRequest(() => api.GET('/api/public/services'))
const eligibleEmployees = computed(() => {
  const all = employees.data.value ?? []
  if (!serviceIds.value.length || !services.data.value) return all
  const offering = serviceIds.value.map(
    (id) => new Set(services.data.value!.find((s) => s.serviceTypeId === id)?.employeeIds ?? []),
  )
  const narrowed = all.filter((e) => offering.every((ids) => ids.has(e.id)))
  // a stale link shouldn't leave nobody to pick
  return narrowed.length ? narrowed : all
})

// step 2: the chosen employee's treatments and products
const offerings = shallowRef<Schemas['EmployeeOfferings']>()
const offeringsError = ref<string>()
async function loadOfferings() {
  const id = employeeId.value
  offerings.value = undefined
  offeringsError.value = undefined
  if (!id) return
  try {
    const { data, error, response } = await api.GET('/api/public/employees/{id}/offerings', {
      params: { path: { id } },
    })
    if (id !== employeeId.value) return
    if (data) {
      offerings.value = data
    } else if (response.status === 404 || response.status === 400) {
      // left the salon, or a mangled link: start over with someone else
      await draft.goTo('employee', { employee: undefined })
      notice.value = t('booking.employeeUnavailable')
    } else {
      offeringsError.value = problemMessage(error, response)
    }
  } catch (e) {
    offeringsError.value = problemMessage(e)
  }
}
watch(employeeId, loadOfferings, { immediate: true })

// keep only what this employee offers, in the quantities they have
watch(offerings, (current) => {
  if (!current) return
  const offered = new Set(current.services.map((s) => s.serviceTypeId))
  const keptServices = serviceIds.value.filter((id) => offered.has(id))
  const keptProducts = products.value
    .map((p) => ({
      ...p,
      quantity: Math.min(p.quantity, current.products.find((o) => o.productId === p.productId)?.inStock ?? 0),
    }))
    .filter((p) => p.quantity > 0)
  if (
    keptServices.length !== serviceIds.value.length ||
    JSON.stringify(keptProducts) !== JSON.stringify(products.value)
  ) {
    void draft.update({ services: keptServices, products: keptProducts })
  }
})

// live price for the sidebar; ignores answers to selections that have since changed
const quote = shallowRef<Schemas['Quote']>()
const quoting = ref(false)
let quoteRequest = 0
watch(
  () => [employeeId.value, serviceIds.value.join(), JSON.stringify(products.value)],
  async () => {
    const request = ++quoteRequest
    if (!employeeId.value || !serviceIds.value.length) {
      quote.value = undefined
      return
    }
    quoting.value = true
    try {
      const { data } = await api.POST('/api/public/quote', {
        body: { employeeId: employeeId.value, serviceIds: serviceIds.value, products: products.value },
      })
      if (request === quoteRequest) quote.value = data
    } catch {
      if (request === quoteRequest) quote.value = undefined
    } finally {
      if (request === quoteRequest) quoting.value = false
    }
  },
  { immediate: true },
)

const employeeName = computed(
  () =>
    offerings.value?.employee.displayName ??
    employees.data.value?.find((e) => e.id === employeeId.value)?.displayName,
)

// step 4: notes survive the login round trip in session storage; the phone goes on the client's profile
const NOTES_KEY = 'nio.booking.notes'
const notes = ref(readNotes())
watch(notes, (value) => {
  try {
    sessionStorage.setItem(NOTES_KEY, value)
  } catch {
    // kept for this page only
  }
})
function readNotes() {
  try {
    return sessionStorage.getItem(NOTES_KEY) ?? ''
  } catch {
    return ''
  }
}

const canBook = computed(() => authenticated.value && hasRole('client'))
const profile = shallowRef<Schemas['CustomerProfile']>()
const phone = ref('')
watch(
  canBook,
  async (yes) => {
    if (!yes) return
    const { data } = await api.GET('/api/me/profile')
    profile.value = data
    phone.value = data?.phone ?? ''
  },
  { immediate: true },
)

const notice = ref<string>()
const submitting = ref(false)
const submitError = ref<string>()
const phoneError = ref<string>()

async function confirm() {
  if (!employeeId.value || !startsAt.value) return
  submitting.value = true
  submitError.value = undefined
  phoneError.value = undefined
  try {
    if (profile.value && phone.value.trim() !== (profile.value.phone ?? '')) {
      const saved = await api.PUT('/api/me/profile', {
        body: { displayName: profile.value.displayName, phone: phone.value.trim() || null },
      })
      if (!saved.data) {
        phoneError.value = problemFields(saved.error).includes('phone')
          ? t('booking.phoneInvalid')
          : problemMessage(saved.error, saved.response)
        return
      }
      profile.value = saved.data
    }

    const { data, error, response } = await api.POST('/api/bookings', {
      body: {
        employeeId: employeeId.value,
        startsAt: startsAt.value,
        serviceIds: serviceIds.value,
        products: products.value,
        notes: notes.value.trim() || null,
      },
    })
    if (data) {
      notes.value = ''
      await router.push({ name: 'my-booking', params: { bookingId: data.id }, query: { confirmed: '1' } })
    } else if (response.status === 409 || response.status === 400) {
      // the time was taken meanwhile or is now past, or a product sold out: go back to what needs choosing
      const soldOut = problemCode(error) === 'out-of-stock'
      if (soldOut) await loadOfferings()
      await draft.goTo(soldOut ? 'services' : 'time', { time: undefined })
      notice.value = problemMessage(error, response)
    } else {
      submitError.value = problemMessage(error, response)
    }
  } catch (e) {
    submitError.value = problemMessage(e)
  } finally {
    submitting.value = false
  }
}

/** Applies the person's own change to the draft; a notice explaining an earlier step change has done its job. */
function choose(changes: DraftChanges, nextStep?: BookingStep) {
  notice.value = undefined
  return nextStep ? draft.goTo(nextStep, changes) : draft.update(changes)
}

const stepLabels: Record<BookingStep, string> = {
  employee: 'booking.steps.employee',
  services: 'booking.steps.services',
  time: 'booking.steps.time',
  review: 'booking.steps.review',
}
const stepIndex = computed(() => bookingSteps.indexOf(step.value))
</script>

<template>
  <div>
    <PageSection narrow>
      <div class="flex flex-col items-center gap-6 text-center">
        <ScriptTitle as="h1" :title="t('booking.title')" :script="t('booking.script')" />
      </div>
    </PageSection>

    <PageSection tone="soft">
      <!-- progress; finished steps can be revisited -->
      <ol class="label-caps mb-12 flex flex-wrap justify-center gap-x-8 gap-y-3 text-sm">
        <li v-for="(s, index) in bookingSteps" :key="s">
          <button
            v-if="index < stepIndex"
            type="button"
            class="uppercase text-ink-muted transition-colors hover:text-accent"
            @click="draft.goTo(s)"
          >
            {{ index + 1 }}. {{ t(stepLabels[s]) }}
          </button>
          <span
            v-else
            :class="index === stepIndex ? 'text-accent' : 'text-ink-muted/60'"
            :aria-current="index === stepIndex ? 'step' : undefined"
          >
            {{ index + 1 }}. {{ t(stepLabels[s]) }}
          </span>
        </li>
      </ol>

      <div class="grid items-start gap-10 lg:grid-cols-[1fr_22rem]">
        <section class="flex flex-col gap-8">
          <h2 class="display-caps text-3xl">{{ t(`booking.headings.${step}`) }}</h2>
          <BaseAlert v-if="notice" tone="danger">{{ notice }}</BaseAlert>

          <!-- 1. employee -->
          <RequestState
            v-if="step === 'employee'"
            :loading="employees.loading.value"
            :error="employees.error.value"
            :empty="eligibleEmployees.length === 0"
            :empty-text="t('team.empty')"
            @retry="employees.reload"
          >
            <EmployeePicker
              :employees="eligibleEmployees"
              :selected-id="employeeId"
              @select="choose({ employee: $event }, 'services')"
            />
          </RequestState>

          <!-- 2. services and products -->
          <template v-else-if="step === 'services'">
            <RequestState
              :loading="!offerings && !offeringsError"
              :error="offeringsError"
              :empty="offerings?.services.length === 0"
              :empty-text="t('team.noOfferings')"
              @retry="loadOfferings"
            >
              <ServicePicker
                v-if="offerings"
                :offerings="offerings"
                :service-ids="serviceIds"
                :products="products"
                @update:service-ids="choose({ services: $event })"
                @update:products="choose({ products: $event })"
              />
            </RequestState>
            <div class="flex justify-end">
              <BaseButton :disabled="!serviceIds.length" @click="draft.goTo('time')">
                {{ t('booking.next') }}
              </BaseButton>
            </div>
          </template>

          <!-- 3. day and time -->
          <template v-else-if="step === 'time' && employeeId">
            <SlotPicker
              :employee-id="employeeId"
              :service-ids="serviceIds"
              :day="day"
              :starts-at="startsAt"
              @update:day="draft.update({ day: $event, time: undefined })"
              @update:starts-at="choose({ time: $event })"
            />
            <div class="flex justify-end">
              <BaseButton :disabled="!startsAt" @click="draft.goTo('review')">{{
                t('booking.next')
              }}</BaseButton>
            </div>
          </template>

          <!-- 4. review and confirm -->
          <template v-else-if="step === 'review'">
            <template v-if="canBook">
              <BaseTextarea
                v-model="notes"
                :label="t('booking.notes')"
                :hint="t('booking.notesHint')"
                maxlength="1000"
              />
              <BaseInput
                v-model="phone"
                type="tel"
                autocomplete="tel"
                maxlength="30"
                :label="t('booking.phone')"
                :hint="t('booking.phoneHint')"
                :error="phoneError"
              />
              <BaseAlert v-if="submitError" tone="danger">{{ submitError }}</BaseAlert>
              <div class="flex justify-end">
                <BaseButton :loading="submitting" @click="confirm">{{ t('booking.confirm') }}</BaseButton>
              </div>
            </template>
            <template v-else-if="authenticated">
              <BaseAlert tone="danger">{{ t('booking.notAClient') }}</BaseAlert>
            </template>
            <template v-else>
              <p class="text-ink-muted">{{ t('booking.loginToConfirm') }}</p>
              <div class="flex justify-end">
                <BaseButton @click="login(route.fullPath)">{{ t('booking.loginButton') }}</BaseButton>
              </div>
            </template>
          </template>
        </section>

        <aside class="lg:sticky lg:top-24">
          <QuoteSummary
            :employee-name="employeeName"
            :quote="serviceIds.length ? quote : undefined"
            :starts-at="startsAt"
            :loading="quoting"
          />
        </aside>
      </div>
    </PageSection>
  </div>
</template>
