<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'

/**
 * One catalog item the employee may offer, with their own price and one more number: a treatment's duration or
 * a product's stock. Saved on its own.
 */
defineProps<{
  name: string
  description?: string | null
  amountLabel: string
  amountMin: number
  amountStep?: number
  priceError?: string
  amountError?: string
  error?: string
  dirty: boolean
  saving: boolean
}>()
const offered = defineModel<boolean>('offered', { required: true })
const price = defineModel<string>('price', { required: true })
const amount = defineModel<string>('amount', { required: true })
defineEmits<{ save: [] }>()

const { t } = useI18n()
</script>

<template>
  <form
    class="grid gap-4 border bg-surface px-5 py-4 transition-colors md:grid-cols-[1fr_8rem_8rem_auto] md:items-start"
    :class="offered ? 'border-accent' : 'border-line-soft'"
    @submit.prevent="$emit('save')"
  >
    <div class="flex flex-col gap-1 md:pt-7">
      <BaseCheckbox v-model="offered" :label="name" />
      <p v-if="description" class="pl-7 text-sm text-ink-muted">{{ description }}</p>
    </div>
    <BaseInput
      v-model="price"
      inputmode="decimal"
      :label="t('staff.offerings.price')"
      :error="priceError"
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
    <div class="flex items-center gap-3 md:pt-7">
      <BaseButton
        type="submit"
        size="sm"
        :loading="saving"
        :disabled="!dirty || !!priceError || !!amountError"
      >
        {{ t('common.save') }}
      </BaseButton>
    </div>
    <BaseAlert v-if="error" tone="danger" class="md:col-span-4">{{ error }}</BaseAlert>
  </form>
</template>
