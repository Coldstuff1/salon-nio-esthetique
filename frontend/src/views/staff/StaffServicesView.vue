<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import PageSection from '@/components/layout/PageSection.vue'
import OfferingRow from '@/components/staff/OfferingRow.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useOfferingRows } from '@/composables/useOfferingRows'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { byName, groupByCategory } from '@/utils/catalog'

/** Which of the salon's treatments the employee does, at what price and in how long. */
const { t } = useI18n()
const { localize } = useLocale()

const catalog = useRequest(() => api.GET('/api/public/service-types'))
const staff = useRequest(() => api.GET('/api/staff/me'))
const categories = computed(() =>
  groupByCategory(byName(catalog.data.value ?? [], localize), (s) => localize(s.category)),
)

const offerings = useOfferingRows({
  profile: staff.data,
  ids: () => (catalog.data.value ?? []).map((s) => s.id),
  current: (profile) =>
    new Map(
      profile.serviceOfferings.map((o) => [
        o.serviceTypeId,
        { priceCents: o.priceCents, amount: o.durationMinutes, active: o.active },
      ]),
    ),
  defaultAmount: 60,
  minAmount: 5,
  amountError: t('staff.offerings.durationInvalid'),
  save: (id, o) =>
    api.PUT('/api/staff/me/service-offerings/{serviceTypeId}', {
      params: { path: { serviceTypeId: id } },
      body: { priceCents: o.priceCents, durationMinutes: o.amount, active: o.active },
    }),
})
</script>

<template>
  <PageSection>
    <div class="flex flex-col gap-8">
      <p class="max-w-2xl text-ink-muted">{{ t('staff.offerings.servicesIntro') }}</p>
      <RequestState
        :loading="catalog.loading.value || staff.loading.value"
        :error="catalog.error.value ?? staff.error.value"
        :empty="categories.length === 0"
        :empty-text="t('services.empty')"
        @retry="(catalog.reload(), staff.reload())"
      >
        <div class="flex flex-col gap-10">
          <section v-for="group in categories" :key="group.category ?? ''" class="flex flex-col gap-3">
            <h2 class="label-caps text-accent">{{ group.category ?? t('services.otherCategory') }}</h2>
            <OfferingRow
              v-for="service in group.items"
              :key="service.id"
              v-model:offered="offerings.row(service.id).offered"
              v-model:price="offerings.row(service.id).price"
              v-model:amount="offerings.row(service.id).amount"
              :name="localize(service.name)!"
              :description="localize(service.description)"
              :amount-label="t('staff.offerings.duration')"
              :amount-min="5"
              :amount-step="5"
              :price-error="offerings.priceError(service.id)"
              :amount-error="offerings.amountError(service.id)"
              :error="offerings.row(service.id).error"
              :dirty="offerings.dirty(service.id)"
              :saving="offerings.row(service.id).saving"
              @save="offerings.save(service.id)"
            />
          </section>
        </div>
      </RequestState>
    </div>
  </PageSection>
</template>
