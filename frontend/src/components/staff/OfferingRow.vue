<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'

/**
 * One catalog item the employee may offer, with their own price and one more number: a treatment's duration or
 * a product's stock. Products also take what a unit cost them (`costLabel`), treatments an optional position in
 * the employee's list (`orderLabel`) and an optional cap on bookings per day (`limitLabel`). Saved on its own.
 */
const props = defineProps<{
  name: string
  description?: string | null
  /** A remark under the description, e.g. what an add-on goes with. */
  note?: string | null
  amountLabel: string
  amountMin: number
  amountStep?: number
  costLabel?: string
  orderLabel?: string
  limitLabel?: string
  priceError?: string
  costError?: string
  amountError?: string
  orderError?: string
  limitError?: string
  error?: string
  dirty: boolean
  saving: boolean
}>()
const offered = defineModel<boolean>('offered', { required: true })
const price = defineModel<string>('price', { required: true })
const cost = defineModel<string>('cost', { default: '' })
const amount = defineModel<string>('amount', { required: true })
const order = defineModel<string>('order', { default: '' })
const limit = defineModel<string>('limit', { default: '' })
defineEmits<{ save: [] }>()

const { t } = useI18n()

/** Grid columns and the error's span, by how many optional inputs are shown (spelled out for Tailwind). */
const layouts = [
  { grid: 'md:grid-cols-[1fr_8rem_8rem_auto]', span: 'md:col-span-4' },
  { grid: 'md:grid-cols-[1fr_8rem_8rem_8rem_auto]', span: 'md:col-span-5' },
  { grid: 'md:grid-cols-[1fr_8rem_8rem_7rem_7rem_auto]', span: 'md:col-span-6' },
]
const layout = computed(
  () => layouts[[props.costLabel, props.orderLabel, props.limitLabel].filter(Boolean).length]!,
)
</script>

<template>
  <form
    class="grid gap-4 border bg-surface px-5 py-4 transition-colors md:items-start"
    :class="[offered ? 'border-accent' : 'border-line-soft', layout.grid]"
    @submit.prevent="$emit('save')"
  >
    <div class="flex flex-col gap-1 md:pt-7">
      <BaseCheckbox v-model="offered" :label="name" />
      <p v-if="description" class="pl-7 text-sm text-ink-muted">{{ description }}</p>
      <p v-if="note" class="pl-7 text-sm text-accent">{{ note }}</p>
    </div>
    <BaseInput
      v-model="price"
      inputmode="decimal"
      :label="t('staff.offerings.price')"
      :error="priceError"
      :disabled="!offered"
    />
    <BaseInput
      v-if="costLabel"
      v-model="cost"
      inputmode="decimal"
      :label="costLabel"
      :error="costError"
      :disabled="!offered"
    />
    <BaseInput
      v-model="amount"
      type="number"
      inputmode="numeric"
      :min="amountMin"
      :step="amountStep ?? 1"
      :label="amountLabel"
      :error="amountError"
      :disabled="!offered"
    />
    <BaseInput
      v-if="orderLabel"
      v-model="order"
      type="number"
      inputmode="numeric"
      :min="1"
      :max="999"
      :label="orderLabel"
      :error="orderError"
      :disabled="!offered"
    />
    <BaseInput
      v-if="limitLabel"
      v-model="limit"
      type="number"
      inputmode="numeric"
      :min="1"
      :max="99"
      :label="limitLabel"
      :error="limitError"
      :disabled="!offered"
    />
    <div class="flex items-center gap-3 md:pt-7">
      <BaseButton
        type="submit"
        size="sm"
        :loading="saving"
        :disabled="!dirty || !!priceError || !!costError || !!amountError || !!orderError || !!limitError"
      >
        {{ t('common.save') }}
      </BaseButton>
    </div>
    <BaseAlert v-if="error" tone="danger" :class="layout.span">{{ error }}</BaseAlert>
  </form>
</template>
