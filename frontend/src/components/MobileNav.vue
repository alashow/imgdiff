<template>
  <div id="mobile-nav" class="lg:hidden bg-slate-950 border-t border-slate-800 px-4 py-2.5 flex items-center justify-around z-20">
    <!-- controls tab button -->
    <button 
      @click="mobileView = 'controls'" 
      :class="mobileView === 'controls' ? 'text-indigo-400 bg-indigo-500/10' : 'text-slate-400 hover:text-white'"
      class="flex flex-col items-center gap-1 py-1.5 px-4 rounded-xl transition-all"
    >
        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/>
          <line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/>
          <line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/>
        </svg>
        <span class="text-[10px] font-semibold">Import</span>
    </button>
    <!-- canvas tab button -->
    <button 
      @click="mobileView = 'canvas'" 
      :class="mobileView === 'canvas' ? 'text-indigo-400 bg-indigo-500/10' : 'text-slate-400 hover:text-white'"
      class="flex flex-col items-center gap-1 py-1.5 px-4 rounded-xl transition-all"
    >
        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect width="18" height="18" x="3" y="3" rx="2" ry="2"/>
          <circle cx="9" cy="9" r="2"/>
          <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>
        </svg>
        <span class="text-[10px] font-semibold">Canvas</span>
    </button>
    <!-- inspector tab button -->
    <button 
      @click="mobileView = 'inspector'" 
      :class="mobileView === 'inspector' ? 'text-indigo-400 bg-indigo-500/10' : 'text-slate-400 hover:text-white'"
      class="flex flex-col items-center gap-1 py-1.5 px-4 rounded-xl transition-all"
    >
        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.38 0 0 1 8 8v.5z"/>
        </svg>
        <span class="text-[10px] font-semibold">Inspector</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { useImgDiff } from '../composables/useImgDiff';

const { mobileView } = useImgDiff();
</script>
