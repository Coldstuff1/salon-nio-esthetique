<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { byName, groupByCategory } from '@/utils/catalog'
import { formatDurationRange, formatPriceRange } from '@/utils/format'

const { t } = useI18n()
const { locale, localize } = useLocale()

const { data: services, loading, error, reload } = useRequest(() => api.GET('/api/public/services'))
const categories = computed(() =>
  groupByCategory(byName(services.value ?? [], localize), (s) => localize(s.category)),
)
</script>

<template>
  <div>
    <PageSection narrow>
      <div class="flex flex-col items-center gap-6 text-center">
        <ScriptTitle as="h1" :title="t('services.title')" :script="t('services.script')" />
        <p class="max-w-lg text-lg text-ink-muted">{{ t('services.intro') }}</p>
      </div>
    </PageSection>

    <PageSection tone="soft">
      <RequestState
        :loading="loading"
        :error="error"
        :empty="categories.length === 0"
        :empty-text="t('services.empty')"
        @retry="reload"
      >
        <div class="flex flex-col gap-16">
          <section v-for="group in categories" :key="group.category ?? ''">
            <h2 class="display-caps mb-8 text-center text-3xl">
              {{ group.category ?? t('services.otherCategory') }}
            </h2>
            <div class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              <BaseCard
                v-for="service in group.items"
                :key="service.serviceTypeId"
                class="flex flex-col gap-4"
              >
                <h3 class="display-caps text-2xl">
                  {{ localize(service.name) }}
                </h3>
                <p v-if="service.description" class="flex-1 text-ink-muted">
                  {{ localize(service.description) }}
                </p>
                <p v-else class="flex-1" />
                <template v-if="service.minPriceCents != null && service.maxPriceCents != null">
                  <div class="flex items-baseline justify-between gap-4 border-t border-line-soft pt-4">
                    <span class="font-medium tabular-nums">
                      {{ formatPriceRange(service.minPriceCents, service.maxPriceCents, locale) }}
                    </span>
                    <span
                      v-if="service.minDurationMinutes != null && service.maxDurationMinutes != null"
                      class="text-sm text-ink-muted"
                    >
                      {{
                        formatDurationRange(service.minDurationMinutes, service.maxDurationMinutes, locale)
                      }}
                    </span>
                  </div>
                  <BaseButton
                    :to="{
                      name: 'booking',
                      query: { services: service.serviceTypeId },
                    }"
                    variant="outline"
                    size="sm"
                  >
                    {{ t('services.book') }}
                  </BaseButton>
                </template>
                <p v-else class="label-caps border-t border-line-soft pt-4 text-ink-muted">
                  {{ t('services.notOffered') }}
                </p>
              </BaseCard>
            </div>
          </section>
        </div>
      </RequestState>
    </PageSection>
  </div>
</template>
