<script setup lang="ts">
import type { Schemas } from '@/api/http'
import EmployeePortrait from '@/components/brand/EmployeePortrait.vue'

/** Step 1: who the appointment is with. */
defineProps<{ employees: Schemas['EmployeeSummary'][]; selectedId?: string }>()
defineEmits<{ select: [id: string] }>()
</script>

<template>
  <ul class="grid grid-cols-[repeat(auto-fill,minmax(11rem,1fr))] gap-6">
    <li v-for="employee in employees" :key="employee.id">
      <button
        type="button"
        class="group flex w-full flex-col gap-3 text-center"
        :aria-pressed="employee.id === selectedId"
        @click="$emit('select', employee.id)"
      >
        <EmployeePortrait
          :name="employee.displayName"
          :photo-url="employee.photoUrl"
          class="transition-colors group-hover:border-accent"
          :class="{ '!border-accent ring-2 ring-blush': employee.id === selectedId }"
        />
        <span class="display-caps text-xl transition-colors group-hover:text-accent">
          {{ employee.displayName }}
        </span>
      </button>
    </li>
  </ul>
</template>
