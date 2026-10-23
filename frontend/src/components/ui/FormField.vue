<script setup lang="ts">
/** Caps label + control slot + hint/error line shared by every form input. */
defineProps<{ id: string; label: string; hint?: string; error?: string }>()
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label :for="id" class="label-caps text-ink-muted">{{ label }}</label>
    <slot />
    <p v-if="error" :id="`${id}-error`" class="text-sm text-danger">{{ error }}</p>
    <p v-else-if="hint" :id="`${id}-hint`" class="text-sm text-ink-muted">{{ hint }}</p>
  </div>
</template>
