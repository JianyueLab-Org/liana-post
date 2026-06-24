<template>
  <section class="card p-5">
    <div v-if="$slots.header" class="mb-4 border-b border-cyan-100 pb-4">
      <slot name="header" />
    </div>
    <slot />
  </section>
</template>
