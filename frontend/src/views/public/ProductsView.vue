<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { byName } from '@/utils/catalog'
import { formatPriceRange } from '@/utils/format'

const { t } = useI18n()
const { locale, localize } = useLocale()

const { data, loading, error, reload } = useRequest(() => api.GET('/api/public/products'))
const products = computed(() => data.value && byName(data.value, localize))
</script>

<template>
  <div>
    <PageSection narrow>
      <div class="flex flex-col items-center gap-6 text-center">
        <ScriptTitle as="h1" :title="t('products.title')" :script="t('products.script')" />
        <p class="max-w-lg text-lg text-ink-muted">{{ t('products.intro') }}</p>
      </div>
    </PageSection>

    <PageSection tone="soft">
      <RequestState
        :loading="loading"
        :error="error"
        :empty="products?.length === 0"
        :empty-text="t('products.empty')"
        @retry="reload"
      >
        <div class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <BaseCard v-for="product in products" :key="product.productId" class="flex flex-col gap-4">
            <h2 class="display-caps text-2xl">{{ localize(product.name) }}</h2>
            <p class="flex-1 text-ink-muted">
              {{ localize(product.description) }}
            </p>
            <div class="flex items-center justify-between gap-4 border-t border-line-soft pt-4">
              <template v-if="product.minPriceCents != null && product.maxPriceCents != null">
                <span class="font-medium tabular-nums">
                  {{ formatPriceRange(product.minPriceCents, product.maxPriceCents, locale) }}
                </span>
                <BaseBadge :tone="product.inStock ? 'success' : 'neutral'">
                  {{ product.inStock ? t('products.inStock') : t('products.outOfStock') }}
                </BaseBadge>
              </template>
              <span v-else class="label-caps text-ink-muted">{{ t('products.notOffered') }}</span>
            </div>
          </BaseCard>
        </div>
      </RequestState>
    </PageSection>
  </div>
</template>
