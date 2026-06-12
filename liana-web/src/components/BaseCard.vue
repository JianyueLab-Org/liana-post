<template>
  <section class="card p-5">
    <div v-if="$slots.header" class="mb-4 flex items-center justify-between gap-3">
      <slot name="header" />
    </div>
    <slot />
  </section>
</template>
