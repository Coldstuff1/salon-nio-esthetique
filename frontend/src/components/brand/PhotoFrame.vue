<script setup lang="ts">
/**
 * Photo in a white mat with a hairline frame. An optional `label` sits on a hairline that runs
 * from the photo to the edge of the screen, like BEFORE/AFTER in the treatment posts.
 */
withDefaults(
  defineProps<{
    src: string
    alt: string
    label?: string
    labelSide?: 'left' | 'right'
    aspect?: string
  }>(),
  { labelSide: 'right', aspect: '4 / 3' },
)
</script>

<template>
  <figure class="relative">
    <div class="border border-line bg-surface p-2.5 sm:p-3">
      <img :src="src" :alt="alt" class="block w-full object-cover" :style="{ aspectRatio: aspect }" />
    </div>
    <figcaption
      v-if="label"
      class="absolute top-1/2 hidden sm:block"
      :class="labelSide === 'right' ? 'left-full w-[50vw]' : 'right-full w-[50vw]'"
    >
      <span
        class="display-caps block pb-1 text-3xl text-accent"
        :class="labelSide === 'right' ? 'pl-8' : 'pr-8 text-right'"
      >
        {{ label }}
      </span>
      <span class="block h-px bg-line" />
    </figcaption>
    <figcaption v-if="label" class="display-caps mt-3 text-center text-2xl text-accent sm:hidden">
      {{ label }}
    </figcaption>
  </figure>
</template>
