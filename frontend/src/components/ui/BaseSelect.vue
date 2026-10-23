<script setup lang="ts" generic="T extends string | number">
import { useId } from 'vue'
import FormField from './FormField.vue'
import { fieldClasses } from './fieldClasses'

defineOptions({ inheritAttrs: false })
defineProps<{
  label: string
  options: { value: T; label: string }[]
  placeholder?: string
  hint?: string
  error?: string
}>()
const model = defineModel<T>()

const id = useId()
</script>

<template>
  <FormField :id="id" :label="label" :hint="hint" :error="error">
    <div class="relative">
      <select
        :id="id"
        v-model="model"
        v-bind="$attrs"
        :aria-invalid="!!error || undefined"
        :aria-describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
        class="appearance-none pr-10"
        :class="fieldClasses(error)"
      >
        <option v-if="placeholder" disabled value="">{{ placeholder }}</option>
        <option v-for="option in options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <svg
        class="pointer-events-none absolute top-1/2 right-3.5 size-3 -translate-y-1/2 text-ink-muted"
        viewBox="0 0 12 12"
        aria-hidden="true"
      >
        <path d="M2 4.5 6 8.5 10 4.5" fill="none" stroke="currentColor" stroke-width="1.25" />
      </svg>
    </div>
  </FormField>
</template>
