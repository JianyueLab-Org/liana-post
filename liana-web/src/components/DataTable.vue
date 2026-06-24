<template>
  <div class="overflow-hidden rounded-lg border border-cyan-100 bg-white/90 shadow-sm shadow-cyan-900/5">
    <div class="overflow-auto">
      <table class="table min-w-full">
        <thead>
          <tr>
            <th v-for="column in columns" :key="column.key">{{ column.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row[idKey]">
            <td v-for="column in columns" :key="column.key">
              <slot :name="column.key" :row="row">{{ row[column.key] }}</slot>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td :colspan="columns.length" class="py-10 text-center text-sm text-slate-500">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  idKey: { type: String, default: 'id' },
});
</script>
