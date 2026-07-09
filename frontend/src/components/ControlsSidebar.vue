<template>
  <aside id="sidebar-controls" class="w-full lg:w-80 bg-slate-950 border-r border-slate-800 flex flex-col overflow-y-auto no-scrollbar shrink-0 h-full">
    <!-- Git/Manual mode toggle -->
    <div class="p-2 bg-slate-900 grid grid-cols-2 gap-1 sticky top-0 z-10 border-b border-slate-800">
      <button @click="setGitMode()" :class="appMode === 'git' ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-400'" class="px-3 py-2 rounded-lg text-xs font-semibold">Git Diff</button>
      <button @click="setManualMode()" :class="appMode === 'manual' ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-400'" class="px-3 py-2 rounded-lg text-xs font-semibold">Manual Compare</button>
    </div>

    <!-- Git Mode -->
    <div v-if="appMode === 'git'" class="p-4 border-b border-slate-800 space-y-3">
      <h3 class="text-[10px] uppercase font-bold tracking-wider text-slate-400 mb-1">Git Repository Path</h3>
      <div class="flex items-center gap-2">
        <input v-model="tempRepoPath" @keyup.enter="scanRepository(tempRepoPath)" type="text" placeholder="Path to Git repo..." class="flex-1 bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-xs text-slate-300 min-w-0"/>
        <button @click="pickAndScanRepository" class="shrink-0 py-2 px-3 bg-slate-800 hover:bg-slate-700 rounded-xl text-xs font-semibold transition-all">Browse</button>
      </div>
      <!-- Recently used paths -->
      <div v-if="recentPaths.length > 0" class="space-y-1">
        <span class="text-[10px] uppercase font-bold tracking-wider text-slate-500">Recent</span>
        <div
          v-for="p in recentPaths" :key="p"
          @click="selectRecentPath(p)"
          class="flex items-center gap-2 px-2 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 cursor-pointer group transition-all"
        >
          <svg class="w-3 h-3 text-slate-500 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
          <span class="text-[11px] text-slate-400 group-hover:text-slate-200 truncate flex-1 font-mono">{{ p }}</span>
        </div>
      </div>
      <button @click="scanRepository(tempRepoPath).then(() => recentPaths = loadRecentPaths())" class="w-full py-2 px-3 bg-indigo-600 hover:bg-indigo-500 rounded-xl text-xs font-semibold transition-all">Scan Repository</button>
    </div>

    <!-- Manual Mode -->
    <div v-if="appMode === 'manual'" class="p-4 border-b border-slate-800 space-y-4">
      <div class="space-y-3">
        <h3 class="text-[10px] uppercase font-bold tracking-wider text-slate-400 mb-1 flex items-center justify-between">
          <span>Batch Folder Import</span>
          <svg class="w-3.5 h-3.5 text-indigo-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h5l2 3h11v10H3z"/><path d="M3 7V5a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v2"/></svg>
        </h3>
        <p class="text-[10px] text-slate-500 leading-tight">Select two directories. Files with identical names will be paired automatically.</p>

        <div class="grid grid-cols-2 gap-2">
          <button type="button" @click="pickManualFolder('A')" class="relative py-2 px-3 bg-slate-900 border border-slate-700 hover:border-indigo-500 rounded-xl transition-all text-center flex flex-col items-center gap-1 cursor-pointer">
            <svg class="w-4 h-4 text-indigo-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h5l2 3h11v10H3z"/><path d="M3 7V5a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v2"/></svg>
            <span class="text-[10px] font-semibold text-slate-300">Folder A</span>
          </button>

          <button type="button" @click="pickManualFolder('B')" class="relative py-2 px-3 bg-slate-900 border border-slate-700 hover:border-sky-500 rounded-xl transition-all text-center flex flex-col items-center gap-1 cursor-pointer">
            <svg class="w-4 h-4 text-sky-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h5l2 3h11v10H3z"/><path d="M3 7V5a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v2"/></svg>
            <span class="text-[10px] font-semibold text-slate-300">Folder B</span>
          </button>
        </div>

        <p class="text-[10px] text-indigo-400 font-semibold italic mt-2 text-center border-t border-slate-800 pt-2">Tip: You can drag & drop 2 folders anywhere on the screen!</p>
      </div>

      <div class="space-y-4">
        <h3 class="text-[10px] uppercase font-bold tracking-wider text-slate-400 mb-2">Single Image Import</h3>
        <div class="grid grid-cols-2 lg:grid-cols-1 gap-3">
          <div
            id="dropzone-a"
            class="border-2 border-dashed border-slate-700 hover:border-indigo-500 rounded-xl p-3 transition-all text-center cursor-pointer bg-slate-900/50 relative overflow-hidden block"
            @click="pickManualImage('A')"
            @dragenter.prevent="handleManualDragEnter"
            @dragover.prevent="handleManualDragOver"
            @dragleave="handleManualDragLeave"
            @drop.prevent="handleManualDrop"
          >
            <div class="relative z-10 space-y-1">
              <svg class="w-5 h-5 mx-auto text-indigo-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
              <span class="text-xs font-semibold block text-indigo-300">Image A</span>
              <span class="text-[9px] text-slate-400 block truncate">Drag here or click</span>
            </div>
          </div>

          <div
            id="dropzone-b"
            class="border-2 border-dashed border-slate-700 hover:border-sky-500 rounded-xl p-3 transition-all text-center cursor-pointer bg-slate-900/50 relative overflow-hidden block"
            @click="pickManualImage('B')"
            @dragenter.prevent="handleManualDragEnter"
            @dragover.prevent="handleManualDragOver"
            @dragleave="handleManualDragLeave"
            @drop.prevent="handleManualDrop"
          >
            <div class="relative z-10 space-y-1">
              <svg class="w-5 h-5 mx-auto text-sky-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
              <span class="text-xs font-semibold block text-sky-300">Image B</span>
              <span class="text-[9px] text-slate-400 block truncate">Drag here or click</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Comparison Mode Selection -->
    <div class="p-4 border-b border-slate-800 space-y-5">
      <div>
        <label class="text-[10px] uppercase font-bold tracking-wider text-slate-400 block mb-2.5">Comparison Mode</label>
        <div class="grid grid-cols-2 gap-2">
          <button @click="setCompareMode('slider')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': compareMode === 'slider', 'text-slate-400 border-slate-800': compareMode !== 'slider'}" class="px-3 py-2.5 rounded-lg text-xs font-semibold border transition-all flex flex-col items-center gap-1.5">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="5.75" x2="8.59" y2="10.25"/></svg>
            <span>Reveal Slider</span>
          </button>
          <button @click="setCompareMode('overlay')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': compareMode === 'overlay', 'text-slate-400 border-slate-800': compareMode !== 'overlay'}" class="px-3 py-2.5 rounded-lg text-xs font-semibold border transition-all flex flex-col items-center gap-1.5">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15.5 1h-8C6.12 1 5 2.12 5 3.5v17C5 21.88 6.12 23 7.5 23h8c1.38 0 2.5-1.12 2.5-2.5v-17C18 2.12 16.88 1 15.5 1z"/><path d="M12 20v-8m-4 4h8"/></svg>
            <span>Overlay Blend</span>
          </button>
          <button @click="setCompareMode('side-by-side')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': compareMode === 'side-by-side', 'text-slate-400 border-slate-800': compareMode !== 'side-by-side'}" class="px-3 py-2.5 rounded-lg text-xs font-semibold border transition-all flex flex-col items-center gap-1.5">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="3" x2="8" y2="21"/><line x1="16" y1="3" x2="16" y2="21"/></svg>
            <span>Side-By-Side</span>
          </button>
          <button @click="setCompareMode('toggle')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': compareMode === 'toggle', 'text-slate-400 border-slate-800': compareMode !== 'toggle'}" class="px-3 py-2.5 rounded-lg text-xs font-semibold border transition-all flex flex-col items-center gap-1.5">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            <span>A/B Flicker</span>
          </button>
        </div>
      </div>

      <!-- Contextual Controls Based on Mode -->
      <div id="controls-contextual" class="space-y-4 pt-1">
        <!-- Slider Controls -->
        <div v-if="compareMode === 'slider'" class="bg-slate-900/40 p-3 rounded-xl border border-slate-800/80">
          <span class="text-[10px] font-bold uppercase text-slate-400 tracking-wide block mb-2">Manual Reveal Slider</span>
          <input type="range" v-model.number="sliderPos" min="0" max="100" class="w-full accent-indigo-500 cursor-pointer"/>
          <div class="flex justify-between text-[10px] text-slate-500 mb-1">
            <span>A: Left</span>
            <span>B: Right</span>
          </div>
          <alignment-controls />
        </div>

        <!-- Overlay Controls -->
        <div v-if="compareMode === 'overlay'" class="space-y-4 bg-slate-900/40 p-3.5 rounded-xl border border-slate-800/80">
          <div>
            <span class="text-[10px] font-bold uppercase text-slate-400 tracking-wide block mb-1.5">Blend Opacity (Image B)</span>
            <input type="range" v-model.number="overlayOpacity" min="0" max="100" class="w-full accent-indigo-500 cursor-pointer"/>
          </div>
          <div class="border-t border-slate-800/80 pt-3">
            <span class="text-[10px] font-bold uppercase text-slate-400 tracking-wide block mb-2">Overlay Filter Blend Mode</span>
            <div class="grid grid-cols-2 gap-1.5">
              <button @click="setBlendFilter('difference')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': overlayBlend === 'difference'}" class="text-[10px] py-1.5 rounded-md border text-slate-400 border-slate-800 font-semibold transition-all">Difference</button>
              <button @click="setBlendFilter('exclusion')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': overlayBlend === 'exclusion'}" class="text-[10px] py-1.5 rounded-md border text-slate-400 border-slate-800 font-semibold transition-all">Exclusion</button>
              <button @click="setBlendFilter('normal')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': overlayBlend === 'normal'}" class="text-[10px] py-1.5 rounded-md border text-slate-400 border-slate-800 font-semibold transition-all">Normal</button>
              <button @click="setBlendFilter('multiply')" :class="{'bg-indigo-500/10 text-indigo-400 border-indigo-500/30': overlayBlend === 'multiply'}" class="text-[10px] py-1.5 rounded-md border text-slate-400 border-slate-800 font-semibold transition-all">Multiply</button>
            </div>
          </div>
          <alignment-controls />
        </div>

        <!-- Toggle/Flicker Controls -->
        <div v-if="compareMode === 'toggle'" class="space-y-4 bg-slate-900/40 p-3.5 rounded-xl border border-slate-800/80">
          <div>
            <span class="text-[10px] font-bold uppercase text-slate-400 tracking-wide block mb-2">Switch Target Frame</span>
            <div class="grid grid-cols-2 gap-2">
              <button @click="toggleState = 'A'" :class="{'bg-indigo-500/15 text-indigo-400 border-indigo-500/30': toggleState === 'A'}" class="py-2 text-xs font-bold rounded-lg border text-slate-400 border-slate-800 transition-all">View A</button>
              <button @click="toggleState = 'B'" :class="{'bg-indigo-500/15 text-indigo-400 border-indigo-500/30': toggleState === 'B'}" class="py-2 text-xs font-bold rounded-lg border text-slate-400 border-slate-800 transition-all">View B</button>
            </div>
          </div>
          <div class="border-t border-slate-800/80 pt-3">
            <span class="text-[10px] font-bold uppercase text-slate-400 tracking-wide block mb-2">Automated Flicker loop</span>
            <div class="flex items-center gap-2">
              <button @click="toggleFlickerActive()" class="flex-1 py-2 rounded-lg font-bold text-xs flex items-center justify-center gap-1 bg-indigo-500 text-slate-950 hover:bg-indigo-400 transition-all">
                <svg v-if="!isFlickering" class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                <svg v-else class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
                <span>{{ isFlickering ? 'Stop Flicker' : 'Start Flicker' }}</span>
              </button>
              <select :value="flickerSpeed" @change="setFlickerSpeed(Number(($event.target as HTMLSelectElement).value))" class="bg-slate-950 text-xs text-slate-300 rounded-lg p-2 border border-slate-800 focus:outline-none focus:border-indigo-500 font-semibold">
                <option value="200">Fast (200ms)</option>
                <option value="500">Medium (500ms)</option>
                <option value="1000">Slow (1000ms)</option>
              </select>
            </div>
          </div>
          <alignment-controls />
        </div>
      </div>

      <!-- Background Presets -->
      <div>
        <label class="text-[10px] uppercase font-bold tracking-wider text-slate-400 block mb-2.5">Backdrop Environment</label>
        <div class="flex items-center gap-2">
          <button @click="setBackdrop('grid-light')" :class="{'border-indigo-500/80 ring-2 ring-indigo-500': backdropMode === 'grid-light'}" class="w-8 h-8 rounded-lg checkerboard border border-slate-800 transition-all cursor-pointer shadow" title="Light Checkerboard"></button>
          <button @click="setBackdrop('grid-dark')" :class="{'border-indigo-500/80 ring-2 ring-indigo-500': backdropMode === 'grid-dark'}" class="w-8 h-8 rounded-lg checkerboard-dark border border-slate-800 transition-all cursor-pointer shadow" title="Dark Checkerboard"></button>
          <button @click="setBackdrop('white')" :class="{'border-indigo-500/80 ring-2 ring-indigo-500': backdropMode === 'white'}" class="w-8 h-8 rounded-lg bg-white border border-slate-800 transition-all cursor-pointer shadow" title="Solid Light"></button>
          <button @click="setBackdrop('dark')" :class="{'border-indigo-500/80 ring-2 ring-indigo-500': backdropMode === 'dark'}" class="w-8 h-8 rounded-lg bg-slate-900 border border-slate-800 transition-all cursor-pointer shadow" title="Solid Dark"></button>
        </div>
      </div>
    </div>

  </aside>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue';
import { useImgDiff } from '../composables/useImgDiff';

const {
  appMode,
  repoPath,
  scanRepository,
  pickRepositoryFolder,
  setGitMode,
  compareMode,
  setCompareMode,
  setManualMode,
  backdropMode,
  setBackdrop,
  sliderPos,
  overlayBlend,
  setBlendFilter,
  overlayOpacityB,
  setContextOpacityVal,
  pickManualFolder,
  pickManualImage,
  handleManualDrop,
  handleManualDragEnter,
  handleManualDragLeave,
  handleManualDragOver,
  toggleState,
  isFlickering,
  flickerSpeed,
  toggleFlickerActive,
  setFlickerSpeed,
  loadRecentPaths,
} = useImgDiff();

const tempRepoPath = ref(repoPath.value);
const recentPaths = ref<string[]>([]);

const overlayOpacity = computed({
  get: () => overlayOpacityB.value * 100,
  set: (val) => setContextOpacityVal(val)
});

watch(repoPath, (newVal) => tempRepoPath.value = newVal);

onMounted(async () => {
  recentPaths.value = loadRecentPaths();
  const lastUsedPath = recentPaths.value[0];

  // Restore the last used path and immediately rescan it on startup.
  if (!repoPath.value && lastUsedPath) {
    tempRepoPath.value = lastUsedPath;
    await scanRepository(lastUsedPath);
    recentPaths.value = loadRecentPaths();
  } else if (!tempRepoPath.value && lastUsedPath) {
    tempRepoPath.value = lastUsedPath;
  }
});

function selectRecentPath(path: string) {
  tempRepoPath.value = path;
  scanRepository(path).then(() => {
    recentPaths.value = loadRecentPaths();
  });
}

async function pickAndScanRepository() {
  const pickedPath = await pickRepositoryFolder();
  if (!pickedPath) return;
  tempRepoPath.value = pickedPath;
  await scanRepository(pickedPath);
  recentPaths.value = loadRecentPaths();
}


</script>

<style scoped>
.checkerboard {
  background-color: #fafafa;
  background-image:
    linear-gradient(45deg, #e5e7eb 25%, transparent 25%),
    linear-gradient(-45deg, #e5e7eb 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #e5e7eb 75%),
    linear-gradient(-45deg, transparent 75%, #e5e7eb 75%);
  background-size: 20px 20px;
  background-position: 0 0, 0 10px, 10px -10px, -10px 0px;
}
.checkerboard-dark {
  background-color: #1e293b;
  background-image:
    linear-gradient(45deg, #0f172a 25%, transparent 25%),
    linear-gradient(-45deg, #0f172a 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #0f172a 75%),
    linear-gradient(-45deg, transparent 75%, #0f172a 75%);
  background-size: 20px 20px;
  background-position: 0 0, 0 10px, 10px -10px, -10px 0px;
}
</style>
