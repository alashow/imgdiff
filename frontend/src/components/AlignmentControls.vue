<template>
  <div class="border-t border-slate-800/80 pt-3 mt-3 space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-[10px] font-bold uppercase text-slate-400 tracking-wide">Manual Alignment</span>
      <button @click="toggleAlignmentLock()" :class="{'bg-indigo-500/10 text-indigo-400': alignmentUnlocked}" class="text-[10px] hover:text-indigo-300 flex items-center gap-1 border border-indigo-500/20 px-2 py-0.5 rounded text-slate-400 transition-all">
        <svg v-if="alignmentUnlocked" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/></svg>
        <svg v-else class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        {{ alignmentUnlocked ? 'Unlocked' : 'Locked' }}
      </button>
    </div>
    <div v-if="alignmentUnlocked" class="grid grid-cols-2 gap-3 pt-1">
      <div>
        <label class="text-[9px] text-slate-500 mb-1 block">X Offset (px)</label>
        <input type="number" v-model.number="offsetX" class="w-full bg-slate-950 border border-slate-800 rounded px-2 py-1 text-xs text-slate-300 focus:border-indigo-500 outline-none transition-colors"/>
      </div>
      <div>
        <label class="text-[9px] text-slate-500 mb-1 block">Y Offset (px)</label>
        <input type="number" v-model.number="offsetY" class="w-full bg-slate-950 border border-slate-800 rounded px-2 py-1 text-xs text-slate-300 focus:border-indigo-500 outline-none transition-colors"/>
      </div>
    </div>
    <p v-else class="text-[10px] text-slate-500 leading-tight">Images perfectly center-aligned by default. Unlock to fine-tune offsets.</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useImgDiff } from '../composables/useImgDiff';

const { alignmentUnlocked, alignOffsetX, alignOffsetY, toggleAlignmentLock, setAlignmentOffset } = useImgDiff();

const offsetX = computed({
  get: () => alignOffsetX.value,
  set: (val) => setAlignmentOffset('x', val)
});

const offsetY = computed({
  get: () => alignOffsetY.value,
  set: (val) => setAlignmentOffset('y', val)
});
</script>

