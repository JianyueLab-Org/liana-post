<template>
  <div class="relative">
    <label class="text-sm font-medium">附加服务 / 备注标签</label>
    <button type="button" class="input mt-1 flex min-h-11 w-full flex-wrap items-center gap-2 text-left" :disabled="disabled" :aria-expanded="open" @click="open = !open">
      <span v-for="code in model" :key="code" class="rounded-full bg-cyan-100 px-3 py-1 text-sm text-cyan-900">{{ code === 'REGISTRATION' ? '挂号' : code }}</span>
      <span v-if="!model.length" class="text-gray-400">点击选择附加服务（可不选）</span>
    </button>
    <div v-if="open" class="absolute z-20 mt-1 w-full rounded-lg border bg-white p-3 shadow-lg">
      <p v-if="!options.length" class="text-sm text-gray-500">此产品暂无可选附加服务</p>
      <label v-for="option in options" :key="option.code" class="flex cursor-pointer items-center gap-2 py-2"><input type="checkbox" :checked="model.includes(option.code)" @change="toggle(option.code)" />{{ option.name }}</label>
      <button type="button" class="btn btn-ghost mt-2" @click="open = false">完成</button>
    </div>
  </div>
</template>
<script setup>
import { ref, watch } from 'vue';
const model = defineModel({ type: Array, default: () => [] });
const props = defineProps({ options: { type: Array, default: () => [] }, disabled: Boolean });
const open = ref(false);
function toggle(code) { model.value = model.value.includes(code) ? model.value.filter(s => s !== code) : [...model.value, code]; }
watch(() => props.options, options => {
  const valid = model.value.filter(s => options.some(o => o.code === s));
  if (valid.length !== model.value.length) model.value = valid;
});
</script>
