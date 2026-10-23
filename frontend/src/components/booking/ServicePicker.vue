<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Schemas } from '@/api/http'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import { useLocale } from '@/i18n'
import { groupByCategory } from '@/utils/catalog'
import { formatDuration, formatPrice } from '@/utils/format'

/** Step 2: the employee's treatments (one or more, done back to back) and products to pick up on site. */
const props = defineProps<{
  offerings: Schemas['EmployeeOfferings']
  serviceIds: string[]
  products: Schemas['ProductSelection'][]
}>()
const emit = defineEmits<{
  'update:serviceIds': [ids: string[]]
  'update:products': [products: Schemas['ProductSelection'][]]
}>()

const { t } = useI18n()
const { locale } = useLocale()

const categories = computed(() => groupByCategory(props.offerings.services))

function toggleService(id: string, on: boolean) {
  emit('update:serviceIds', on ? [...props.serviceIds, id] : props.serviceIds.filter((s) => s !== id))
}

const quantity = (productId: string) => props.products.find((p) => p.productId === productId)?.quantity ?? 0

function setQuantity(productId: string, value: number) {
  const others = props.products.filter((p) => p.productId !== productId)
  emit('update:products', value > 0 ? [...others, { productId, quantity: value }] : others)
}
</script>

<template>
  <div class="flex flex-col gap-10">
    <fieldset v-for="group in categories" :key="group.category ?? ''" class="flex flex-col gap-3">
      <legend class="label-caps mb-3 text-accent">{{ group.category ?? t('services.otherCategory') }}</legend>
      <div
        v-for="service in group.items"
        :key="service.serviceTypeId"
        class="flex items-start justify-between gap-4 border bg-surface px-4 py-3.5 transition-colors"
        :class="serviceIds.includes(service.serviceTypeId) ? 'border-accent' : 'border-line-soft'"
      >
        <div class="flex flex-col gap-1">
          <BaseCheckbox
            :label="service.name"
            :model-value="serviceIds.includes(service.serviceTypeId)"
            @update:model-value="toggleService(service.serviceTypeId, !!$event)"
          />
          <p v-if="service.description" class="pl-7 text-sm text-ink-muted">{{ service.description }}</p>
        </div>
        <div class="shrink-0 text-right">
          <p class="font-medium tabular-nums">{{ formatPrice(service.priceCents, locale) }}</p>
          <p class="text-sm text-ink-muted">{{ formatDuration(service.durationMinutes, locale) }}</p>
        </div>
      </div>
    </fieldset>

    <fieldset v-if="offerings.products.length" class="flex flex-col gap-3">
      <legend class="label-caps mb-1 text-accent">{{ t('booking.addProducts') }}</legend>
      <p class="mb-2 text-sm text-ink-muted">{{ t('booking.productsHint') }}</p>
      <div
        v-for="product in offerings.products"
        :key="product.productId"
        class="flex items-center justify-between gap-4 border border-line-soft bg-surface px-4 py-3.5"
      >
        <div class="flex flex-col gap-0.5">
          <span>{{ product.name }}</span>
          <span class="text-sm text-ink-muted tabular-nums">
            {{ formatPrice(product.priceCents, locale) }}
            <template v-if="product.inStock === 0"> · {{ t('products.outOfStock') }}</template>
          </span>
        </div>
        <div
          v-if="product.inStock > 0"
          class="flex shrink-0 items-center border border-line-soft"
          role="group"
          :aria-label="t('booking.quantityOf', { name: product.name })"
        >
          <button
            type="button"
            class="px-3 py-1.5 transition-colors hover:text-accent disabled:opacity-40"
            :disabled="quantity(product.productId) === 0"
            :aria-label="t('booking.less')"
            @click="setQuantity(product.productId, quantity(product.productId) - 1)"
          >
            −
          </button>
          <span class="w-8 text-center tabular-nums" aria-live="polite">{{
            quantity(product.productId)
          }}</span>
          <button
            type="button"
            class="px-3 py-1.5 transition-colors hover:text-accent disabled:opacity-40"
            :disabled="quantity(product.productId) >= product.inStock"
            :aria-label="t('booking.more')"
            @click="setQuantity(product.productId, quantity(product.productId) + 1)"
          >
            +
          </button>
        </div>
      </div>
    </fieldset>
  </div>
</template>
