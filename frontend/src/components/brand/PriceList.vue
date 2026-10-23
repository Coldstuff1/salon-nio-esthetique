<script setup lang="ts">
/** The bordered price box from the price-list post. Holds one or more `PriceSection`s of `PriceRow`s. */
</script>

<template>
  <div class="mx-auto w-full max-w-xl border border-line bg-surface px-5 py-8 sm:px-10">
    <div class="flex flex-col gap-8">
      <slot />
    </div>
  </div>
</template>
