<script setup lang="ts">
import { useId } from 'vue'
import FormField from './FormField.vue'
import { fieldClasses } from './fieldClasses'

defineOptions({ inheritAttrs: false })
defineProps<{ label: string; hint?: string; error?: string }>()
const model = defineModel<string | number>()

const id = useId()
</script>

<template>
  <FormField :id="id" :label="label" :hint="hint" :error="error">
    <input
      :id="id"
      v-model="model"
      v-bind="$attrs"
      :aria-invalid="!!error || undefined"
      :aria-describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
      :class="fieldClasses(error)"
    />
  </FormField>
</template>
