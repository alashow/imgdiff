<template>
  <header class="bg-slate-950 border-b border-slate-800 px-6 py-4 flex items-center z-10 shadow-md shrink-0">
    <div class="flex items-center gap-3">
      <div class="bg-indigo-500/15 text-indigo-400 p-2.5 rounded-xl border border-indigo-500/20 shadow-inner">
        <img src="@/assets/ImgDiff.svg" alt="ImgDiff" class="w-6 h-6" />
      </div>
      <div>
        <h1 class="text-xl font-bold tracking-tight text-white">ImgDiff</h1>
        <p class="text-xs text-slate-400">{{ isStandalone ? 'Image comparison tool' : 'Git and local folder image comparison tool' }}</p>
      </div>
    </div>
  </header>
</template>
<script setup lang="ts">
import { useImgDiff } from '../composables/useImgDiff';

const { isStandalone } = useImgDiff();
</script>
