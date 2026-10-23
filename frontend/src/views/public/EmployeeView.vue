<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { api } from '@/api/http'
import EmployeePortrait from '@/components/brand/EmployeePortrait.vue'
import PriceList from '@/components/brand/PriceList.vue'
import PriceRow from '@/components/brand/PriceRow.vue'
import PriceSection from '@/components/brand/PriceSection.vue'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { usePageTitle } from '@/composables/usePageTitle'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import NotFoundView from '@/views/NotFoundView.vue'
import { groupByCategory } from '@/utils/catalog'
import { formatDuration, formatPrice } from '@/utils/format'

const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()

const employeeId = computed(() => String(route.params.employeeId))
const { data, loading, error, status, reload } = useRequest(() =>
  api.GET('/api/public/employees/{id}/offerings', {
    params: { path: { id: employeeId.value } },
  }),
)
// the same page component is reused when going from one profile to another
watch(employeeId, reload)

// unknown or deactivated employee; a malformed id in the URL comes back as 400
const notFound = computed(() => status.value === 404 || status.value === 400)
usePageTitle(() => (notFound.value ? t('errors.notFoundTitle') : data.value?.employee.displayName))

const categories = computed(() => groupByCategory(data.value?.services ?? []))

function serviceNote(service: { durationMinutes: number; description?: string | null }) {
  const duration = formatDuration(service.durationMinutes, locale.value)
  return service.description ? `${duration} · ${service.description}` : duration
}
</script>

<template>
  <NotFoundView v-if="notFound" />
  <div v-else>
    <PageSection>
      <RequestState :loading="loading" :error="error" @retry="reload">
        <div v-if="data" class="grid items-center gap-12 md:grid-cols-[1fr_1.3fr] md:gap-16">
          <EmployeePortrait
            :name="data.employee.displayName"
            :photo-url="data.employee.photoUrl"
            class="mx-auto w-full max-w-sm"
          />
          <div class="flex flex-col items-center gap-7 text-center md:items-start md:text-left">
            <p class="label-caps text-accent">{{ t('team.member') }}</p>
            <ScriptTitle as="h1" :title="data.employee.displayName" />
            <p v-if="data.employee.bio" class="max-w-md text-lg whitespace-pre-line text-ink-muted">
              {{ data.employee.bio }}
            </p>
            <BaseButton
              v-if="data.services.length"
              :to="{ name: 'booking', query: { employee: data.employee.id } }"
            >
              {{ t('team.bookWith', { name: data.employee.displayName }) }}
            </BaseButton>
          </div>
        </div>
      </RequestState>
    </PageSection>

    <PageSection v-if="data" tone="soft">
      <p v-if="!data.services.length && !data.products.length" class="text-center text-ink-muted">
        {{ t('team.noOfferings') }}
      </p>
      <PriceList v-else>
        <PriceSection
          v-for="group in categories"
          :key="group.category ?? ''"
          :title="group.category ?? t('services.otherCategory')"
        >
          <PriceRow
            v-for="service in group.items"
            :key="service.serviceTypeId"
            :name="service.name"
            :price="formatPrice(service.priceCents, locale)"
            :note="serviceNote(service)"
          />
        </PriceSection>
        <PriceSection v-if="data.products.length" :title="t('nav.products')">
          <PriceRow
            v-for="product in data.products"
            :key="product.productId"
            :name="product.name"
            :price="formatPrice(product.priceCents, locale)"
            :note="product.inStock > 0 ? (product.description ?? undefined) : t('products.outOfStock')"
          />
        </PriceSection>
      </PriceList>
    </PageSection>
  </div>
</template>
