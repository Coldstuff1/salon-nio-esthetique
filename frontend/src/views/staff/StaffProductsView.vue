<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import PageSection from '@/components/layout/PageSection.vue'
import OfferingRow from '@/components/staff/OfferingRow.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useOfferingRows } from '@/composables/useOfferingRows'
import { useRequest } from '@/composables/useRequest'

/** Which of the salon's products the employee sells, at what price, and how many they have on hand. */
const { t } = useI18n()

const catalog = useRequest(() => api.GET('/api/public/products'))
const staff = useRequest(() => api.GET('/api/staff/me'))

const offerings = useOfferingRows({
  profile: staff.data,
  ids: () => (catalog.data.value ?? []).map((p) => p.productId),
  current: (profile) =>
    new Map(
      profile.productOfferings.map((o) => [
        o.productId,
        { priceCents: o.priceCents, amount: o.quantityOnHand, active: o.active },
      ]),
    ),
  defaultAmount: 0,
  minAmount: 0,
  amountError: t('staff.offerings.stockInvalid'),
  save: (id, o) =>
    api.PUT('/api/staff/me/product-offerings/{productId}', {
      params: { path: { productId: id } },
      body: { priceCents: o.priceCents, quantityOnHand: o.amount, active: o.active },
    }),
})
</script>

<template>
  <PageSection>
    <div class="flex flex-col gap-8">
      <p class="max-w-2xl text-ink-muted">{{ t('staff.offerings.productsIntro') }}</p>
      <RequestState
        :loading="catalog.loading.value || staff.loading.value"
        :error="catalog.error.value ?? staff.error.value"
        :empty="catalog.data.value?.length === 0"
        :empty-text="t('products.empty')"
        @retry="(catalog.reload(), staff.reload())"
      >
        <div class="flex flex-col gap-3">
          <OfferingRow
            v-for="product in catalog.data.value"
            :key="product.productId"
            v-model:offered="offerings.row(product.productId).offered"
            v-model:price="offerings.row(product.productId).price"
            v-model:amount="offerings.row(product.productId).amount"
            :name="product.name"
            :description="product.description"
            :amount-label="t('staff.offerings.stock')"
            :amount-min="0"
            :price-error="offerings.priceError(product.productId)"
            :amount-error="offerings.amountError(product.productId)"
            :error="offerings.row(product.productId).error"
            :dirty="offerings.dirty(product.productId)"
            :saving="offerings.row(product.productId).saving"
            @save="offerings.save(product.productId)"
          />
        </div>
      </RequestState>
    </div>
  </PageSection>
</template>
