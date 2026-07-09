<template>
  <section 
    id="viewport-stage" 
    class="flex-1 relative overflow-hidden flex flex-col transition-all duration-200"
    @dragenter.prevent="handleManualDragEnter"
    @dragover.prevent="handleManualDragOver"
    @dragleave="handleManualDragLeave"
    @drop.prevent="handleManualDrop"
    :class="{
      'checkerboard': backdropMode === 'grid-light',
      'checkerboard-dark': backdropMode === 'grid-dark',
      'bg-white': backdropMode === 'white',
      'bg-slate-900': backdropMode === 'dark'
    }"
  >
    <!-- Floating Controls -->
    <div class="absolute top-4 right-4 z-20 flex items-center gap-1.5 bg-slate-950/90 border border-slate-800 p-1.5 rounded-xl shadow-lg backdrop-blur">
      <!-- Reset pan/zoom -->
      <button @click="resetStageView" class="p-1.5 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition-all" title="Reset Pan & Zoom">
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
      </button>
      <div class="w-px h-4 bg-slate-800"></div>
      <button @click="toggleScaleMatch" :class="scaleMatchActive ? 'text-indigo-400 hover:text-indigo-300' : 'text-slate-400 hover:text-white'" class="p-1.5 hover:bg-slate-800 rounded-lg text-xs font-semibold px-2 flex items-center gap-1 transition-all" :title="scaleMatchActive ? 'Fit window enabled' : 'Original size mode enabled'">
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"/></svg>
        {{ scaleMatchActive ? 'Fit Window' : 'Original Size' }}
      </button>
      <span class="px-2 py-1 rounded-lg bg-slate-900 border border-slate-800 text-[11px] font-semibold code-font text-indigo-400" title="Current zoom level">
        {{ Math.round(zoom * 100) }}%
      </span>
      <div class="w-px h-4 bg-slate-800"></div>
      <!-- Drag lock toggle -->
      <button @click="toggleDragLock" :class="isDragLocked ? 'text-amber-400 hover:text-amber-300' : 'text-slate-400 hover:text-white'" class="p-1.5 hover:bg-slate-800 rounded-lg transition-all" :title="isDragLocked ? 'Drag locked — click to enable pan' : 'Drag enabled — click to lock'">
        <!-- Locked icon -->
        <svg v-if="isDragLocked" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        <!-- Unlocked icon -->
        <svg v-else class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/></svg>
      </button>
    </div>

    <!-- Comparison Container -->
    <div
      id="interactive-container" 
      ref="interactiveContainer"
      class="flex-1 w-full h-full relative overflow-hidden flex items-center justify-center comparison-stage"
      @mousedown="handleMouseDown"
      @mousemove="handleMouseMove"
      @mouseup="handleMouseUp"
      @mouseleave="handleMouseUp"
      @wheel="handleWheel"
      :style="{ cursor: isDragLocked ? 'default' : isPanning ? 'grabbing' : 'grab' }"
    >
      <div
        v-if="loadingIndicatorVisible"
        class="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/25 pointer-events-none"
      >
        <div class="flex items-center gap-2 rounded-lg border border-slate-700/70 bg-slate-950/85 px-3 py-2 text-xs text-slate-200">
          <svg class="w-4 h-4 animate-spin text-indigo-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" class="opacity-30"/>
            <path d="M21 12a9 9 0 0 0-9-9"/>
          </svg>
          Loading image...
        </div>
      </div>

      <!-- Comparison modes: only shown when images are loaded -->
      <template v-if="currentPair.name">
        <!-- SLIDER MODE -->
        <div v-if="compareMode === 'slider'" class="absolute inset-0 flex items-center justify-center p-8">
          <div class="pan-container origin-center select-none" :style="{ transform: transformStyle }">
            <div ref="sliderWrapper" class="diff-wrapper shadow-2xl transition-all">
              <img v-if="currentPair.urlA" :src="currentPair.urlA" @load="onLoadA" class="diff-img rounded" :style="scaleMatchActive ? fitImageStyle : undefined"/>
              <img v-else-if="currentPair.urlB" :src="currentPair.urlB" @load="onLoadB" class="diff-img rounded" :style="scaleMatchActive ? fitImageStyle : undefined"/>
              <img v-if="hasBothCompareImages" :src="currentPair.urlB" @load="onLoadB" class="diff-img diff-img-b rounded"
                :style="[
                  scaleMatchActive ? fitImageStyle : undefined,
                  {
                    clipPath: `inset(0 ${100 - sliderPos}% 0 0)`,
                    '--offset-x': `${alignOffsetX}px`,
                    '--offset-y': `${alignOffsetY}px`
                  }
                ]"
              />
              <div v-if="hasBothCompareImages" @mousedown.stop="startSlideDrag" class="absolute top-0 bottom-0 w-0.5 bg-white cursor-ew-resize flex items-center justify-center pointer-events-auto z-10" :style="{ left: `${sliderPos}%`, transform: 'translateX(-50%)' }">
                <div class="absolute w-8 h-8 bg-slate-950 text-white rounded-full border border-slate-700 shadow-xl flex items-center justify-center flex-col shrink-0 hover:scale-110 active:scale-95 transition-transform">
                  <div class="flex items-center gap-0.5">
                    <svg class="w-3 h-3 text-slate-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
                    <svg class="w-3 h-3 text-slate-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- OVERLAY MODE -->
        <div v-if="compareMode === 'overlay'" class="absolute inset-0 flex items-center justify-center p-8">
          <div class="pan-container origin-center select-none" :style="{ transform: transformStyle }">
            <div class="diff-wrapper shadow-2xl">
              <img v-if="currentPair.urlA" :src="currentPair.urlA" @load="onLoadA" class="diff-img rounded opacity-100 mix-blend-normal" :style="scaleMatchActive ? fitImageStyle : undefined"/>
              <img v-else-if="currentPair.urlB" :src="currentPair.urlB" @load="onLoadB" class="diff-img rounded" :style="scaleMatchActive ? fitImageStyle : undefined"/>
              <img v-if="hasBothCompareImages" :src="currentPair.urlB" @load="onLoadB" class="diff-img diff-img-b rounded"
                :style="[
                  scaleMatchActive ? fitImageStyle : undefined,
                  {
                    opacity: overlayOpacityB,
                    mixBlendMode: overlayBlend,
                    '--offset-x': `${alignOffsetX}px`,
                    '--offset-y': `${alignOffsetY}px`
                  }
                ]"
              />
            </div>
          </div>
        </div>

        <!-- SIDE-BY-SIDE MODE -->
        <div v-if="compareMode === 'side-by-side'" class="absolute inset-0 flex items-center justify-center p-8">
          <div class="pan-container origin-center select-none flex gap-4 lg:gap-8" :style="{ transform: transformStyle }">
            <div class="flex flex-col items-center gap-3">
              <span class="px-2 py-1 bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 rounded text-[10px] uppercase font-bold tracking-widest">Image Source A</span>
              <div class="shadow-2xl flex items-center justify-center relative">
                <img :src="currentPair.urlA" @load="onLoadA" class="diff-img rounded border border-slate-700/40" :style="scaleMatchActive ? fitSideImageStyle : originalSideImageStyle"/>
              </div>
            </div>
            <div class="flex flex-col items-center gap-3">
              <span class="px-2 py-1 bg-sky-500/10 text-sky-400 border border-sky-500/20 rounded text-[10px] uppercase font-bold tracking-widest">Image Source B</span>
              <div class="shadow-2xl flex items-center justify-center relative">
                <img :src="currentPair.urlB" @load="onLoadB" class="diff-img rounded border border-slate-700/40" :style="scaleMatchActive ? fitSideImageStyle : originalSideImageStyle"/>
              </div>
            </div>
          </div>
        </div>

        <!-- TOGGLE/FLICKER MODE -->
        <div v-if="compareMode === 'toggle'" class="absolute inset-0 flex items-center justify-center p-8">
          <div class="pan-container origin-center select-none" :style="{ transform: transformStyle }">
            <div class="diff-wrapper shadow-2xl">
              <!-- Keep image A in normal flow at all times so the wrapper never collapses.
                   Use visibility instead of v-show (display:none) to hide it. -->
              <img :src="currentPair.urlA" @load="onLoadA" class="diff-img rounded"
                :style="[
                  scaleMatchActive ? fitImageStyle : undefined,
                  { visibility: toggleState === 'A' ? 'visible' : 'hidden' }
                ]"
              />
              <img :src="currentPair.urlB" @load="onLoadB" class="diff-img diff-img-b rounded"
                :style="[
                  scaleMatchActive ? fitImageStyle : undefined,
                  {
                    visibility: toggleState === 'B' ? 'visible' : 'hidden',
                    '--offset-x': `${alignOffsetX}px`,
                    '--offset-y': `${alignOffsetY}px`
                  }
                ]"
              />
            </div>
          </div>
        </div>
      </template>

      <!-- Empty State -->
      <div v-else class="text-center flex flex-col items-center gap-2">
        <svg class="w-16 h-16 text-slate-700" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
        <p class="text-slate-400">{{ isStandalone ? 'No images loaded. Pick or drop images to begin comparison.' : 'No images loaded. Scan a Git repository or switch to Manual mode.' }}</p>
      </div>
    </div>

    <!-- Drop Overlay -->
    <div
      id="drop-overlay"
      class="absolute inset-0 bg-slate-900/90 backdrop-blur border-4 border-dashed border-indigo-500 m-4 rounded-2xl flex items-center justify-center flex-col gap-3 pointer-events-none z-50 transition-opacity duration-200"
      :class="isDropActive ? 'opacity-100' : 'opacity-0'"
    >
      <svg class="w-16 h-16 text-indigo-400 animate-bounce" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
      <h2 class="text-2xl font-bold text-white">{{ dropMessage }}</h2>
      <p class="text-sm text-slate-400">Release 2 folders to batch process, or 2 single images to compare directly</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { useImgDiff } from '../composables/useImgDiff';

const {
  isStandalone,
  backdropMode,
  currentPair,
  compareMode,
  zoom,
  panX,
  panY,
  sliderPos,
  scaleMatchActive,
  isDragLocked,
  alignOffsetX,
  alignOffsetY,
  overlayOpacityB,
  overlayBlend,
  toggleState,
  isDropActive,
  dropMessage,
  isImageLoading,
  resetStageView,
  toggleScaleMatch,
  toggleDragLock,
  handleManualDrop,
  handleManualDragEnter,
  handleManualDragLeave,
  handleManualDragOver,
  updateDimensionsA,
  updateDimensionsB
} = useImgDiff();

const isPanning = ref(false);
const startMouseX = ref(0);
const startMouseY = ref(0);
const sliderWrapper = ref<HTMLElement | null>(null);
const interactiveContainer = ref<HTMLElement | null>(null);
const containerWidth = ref(0);
const containerHeight = ref(0);
const loadingIndicatorVisible = ref(false);

let rafPan: number | null = null;
let resizeObserver: ResizeObserver | null = null;
let loadingIndicatorTimer: ReturnType<typeof setTimeout> | null = null;

const transformStyle = computed(() => `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`);
const hasBothCompareImages = computed(() => !!currentPair.urlA && !!currentPair.urlB);

const fitImageStyle = computed(() => {
  const padding = 64;
  const maxWidth = Math.max(containerWidth.value - padding, 120);
  const maxHeight = Math.max(containerHeight.value - padding, 120);
  return {
    maxWidth: `${maxWidth}px`,
    maxHeight: `${maxHeight}px`
  };
});

const fitSideImageStyle = computed(() => {
  const stagePadding = 64;
  const interImageGap = containerWidth.value >= 1024 ? 32 : 16;
  const labelOffset = 52;
  const maxWidth = Math.max((containerWidth.value - stagePadding - interImageGap) / 2, 120);
  const maxHeight = Math.max(containerHeight.value - stagePadding - labelOffset, 120);
  return {
    maxWidth: `${Math.floor(maxWidth)}px`,
    maxHeight: `${Math.floor(maxHeight)}px`
  };
});

const originalSideImageStyle = computed(() => {
  const stagePadding = 64;
  const labelOffset = 52;
  const maxHeight = Math.max(containerHeight.value - stagePadding - labelOffset, 120);
  return {
    // Preserve original-size width, but prevent tall screenshots from clipping in side-by-side.
    maxHeight: `${Math.floor(maxHeight)}px`
  };
});

function updateContainerSize() {
  const el = interactiveContainer.value;
  if (!el) return;
  containerWidth.value = el.clientWidth;
  containerHeight.value = el.clientHeight;
}

onMounted(async () => {
  await nextTick();
  updateContainerSize();
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => updateContainerSize());
    if (interactiveContainer.value) resizeObserver.observe(interactiveContainer.value);
  }
  window.addEventListener('resize', updateContainerSize);
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (loadingIndicatorTimer) {
    clearTimeout(loadingIndicatorTimer);
    loadingIndicatorTimer = null;
  }
  window.removeEventListener('resize', updateContainerSize);
});

watch(isImageLoading, loading => {
  if (loadingIndicatorTimer) {
    clearTimeout(loadingIndicatorTimer);
    loadingIndicatorTimer = null;
  }

  if (!loading) {
    loadingIndicatorVisible.value = false;
    return;
  }

  // Delay indicator to avoid flashing on instant cache hits.
  loadingIndicatorTimer = setTimeout(() => {
    loadingIndicatorVisible.value = true;
    loadingIndicatorTimer = null;
  }, 140);
});

function handleMouseDown(e: MouseEvent) {
  if (isDragLocked.value) return;
  const target = e.target as HTMLElement;
  if (target.closest('[data-interactive]')) return;
  isPanning.value = true;
  startMouseX.value = e.clientX - panX.value;
  startMouseY.value = e.clientY - panY.value;
}

function handleMouseMove(e: MouseEvent) {
  if (!isPanning.value) return;
  const nx = e.clientX - startMouseX.value;
  const ny = e.clientY - startMouseY.value;
  if (rafPan) return;
  rafPan = requestAnimationFrame(() => {
    panX.value = nx;
    panY.value = ny;
    rafPan = null;
  });
}

function handleMouseUp() {
  isPanning.value = false;
}

function handleWheel(e: WheelEvent) {
  e.preventDefault();
  const factor = Math.exp(e.deltaY * -0.001);
  zoom.value = Math.max(0.1, Math.min(zoom.value * factor, 25));
}

function startSlideDrag(e: MouseEvent) {
  e.preventDefault();
  const wrapper = sliderWrapper.value;
  if (!wrapper) return;

  function move(event: MouseEvent) {
    if (!wrapper) return;
    const rect = wrapper.getBoundingClientRect();
    const pct = ((event.clientX - rect.left) / rect.width) * 100;
    sliderPos.value = Math.max(0, Math.min(100, pct));
  }

  function stop() {
    window.removeEventListener('mousemove', move);
    window.removeEventListener('mouseup', stop);
  }

  window.addEventListener('mousemove', move);
  window.addEventListener('mouseup', stop);
}

function onLoadA(e: Event) {
  const img = e.target as HTMLImageElement;
  updateDimensionsA(img.naturalWidth, img.naturalHeight);
}

function onLoadB(e: Event) {
  const img = e.target as HTMLImageElement;
  updateDimensionsB(img.naturalWidth, img.naturalHeight);
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
.comparison-stage {
  user-select: none;
}
.code-font {
  font-family: 'Fira Code', monospace;
}
.diff-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.diff-img {
  display: block;
  pointer-events: none;
}
.diff-img-b {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(calc(-50% + var(--offset-x, 0px)), calc(-50% + var(--offset-y, 0px)));
  will-change: clip-path, transform;
}
.pan-container {
  will-change: transform;
}
</style>

