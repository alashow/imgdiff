<template>
  <div 
    id="toast" 
    class="fixed bottom-6 right-6 bg-slate-950 text-white px-4 py-3 rounded-xl border border-slate-800 shadow-2xl flex items-center gap-2 transform transition-all duration-300 pointer-events-none z-50"
    :class="showToastActive ? 'translate-y-0 opacity-100' : 'translate-y-24 opacity-0'"
  >
    <svg class="text-indigo-400 w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
      <polyline points="22 4 12 14.01 9 11.01"/>
    </svg>
    <span class="text-sm font-semibold">{{ toastMsg }}</span>
  </div>
</template>

<script setup lang="ts">
import { useImgDiff } from '../composables/useImgDiff';

const { toastMsg, showToastActive } = useImgDiff();
</script>
