<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useImgDiff } from './composables/useImgDiff';

import Header from './components/Header.vue';
import ControlsSidebar from './components/ControlsSidebar.vue';
import ComparisonCanvas from './components/ComparisonCanvas.vue';
import InspectorPanel from './components/InspectorPanel.vue';
import MobileNav from './components/MobileNav.vue';
import Toast from './components/Toast.vue';

const { mobileView, compareMode, sliderPos, navigateFiles } = useImgDiff();
const isMobile = ref(false);
const hoveredSection = ref<'canvas' | 'inspector'>('canvas');
const leftPressed = ref(false);
const rightPressed = ref(false);
let sliderRaf: number | null = null;
let lastSliderTs = 0;

function checkMobile() { isMobile.value = window.innerWidth < 1024; }

function stopSliderAnimation() {
  if (sliderRaf !== null) {
    cancelAnimationFrame(sliderRaf);
    sliderRaf = null;
  }
  lastSliderTs = 0;
}

function animateSlider(ts: number) {
  if (hoveredSection.value === 'inspector' || compareMode.value !== 'slider') {
    stopSliderAnimation();
    return;
  }

  const direction = (rightPressed.value ? 1 : 0) - (leftPressed.value ? 1 : 0);
  if (direction === 0) {
    stopSliderAnimation();
    return;
  }

  if (lastSliderTs === 0) lastSliderTs = ts;
  const dt = Math.min((ts - lastSliderTs) / 1000, 0.05);
  lastSliderTs = ts;
  const speedPctPerSec = 28;
  sliderPos.value = Math.max(0, Math.min(100, sliderPos.value + direction * speedPctPerSec * dt));
  sliderRaf = requestAnimationFrame(animateSlider);
}

function ensureSliderAnimation() {
  if (sliderRaf === null) {
    sliderRaf = requestAnimationFrame(animateSlider);
  }
}

function handleGlobalKeyDown(e: KeyboardEvent) {
  const tag = (document.activeElement as HTMLElement)?.tagName ?? '';
  if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tag)) return;

  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
    const dir = e.key === 'ArrowLeft' ? -1 : 1;
    if (hoveredSection.value === 'inspector') {
      e.preventDefault();
      navigateFiles(dir as 1 | -1, 'keyboard');
    } else if (compareMode.value === 'slider') {
      e.preventDefault();
      if (e.key === 'ArrowLeft') leftPressed.value = true;
      if (e.key === 'ArrowRight') rightPressed.value = true;
      ensureSliderAnimation();
    }
  }

  if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
    const dir = e.key === 'ArrowUp' ? -1 : 1;
    e.preventDefault();
    navigateFiles(dir as 1 | -1, 'keyboard');
  }
}

function handleGlobalKeyUp(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft') leftPressed.value = false;
  if (e.key === 'ArrowRight') rightPressed.value = false;
  if (!leftPressed.value && !rightPressed.value) {
    stopSliderAnimation();
  }
}

onMounted(() => {
  checkMobile();
  window.addEventListener('resize', checkMobile);
  window.addEventListener('keydown', handleGlobalKeyDown);
  window.addEventListener('keyup', handleGlobalKeyUp);
});

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile);
  window.removeEventListener('keydown', handleGlobalKeyDown);
  window.removeEventListener('keyup', handleGlobalKeyUp);
  stopSliderAnimation();
});
</script>

<template>
  <div class="bg-slate-900 text-slate-100 h-screen flex flex-col overflow-hidden">
    <Header />
    <main class="flex-1 min-h-0 flex flex-col lg:flex-row overflow-hidden relative">
      <ControlsSidebar class="lg:flex min-h-0" :class="{ 'hidden': isMobile && mobileView !== 'controls', 'flex w-full h-full min-h-0': isMobile && mobileView === 'controls' }"/>
      <ComparisonCanvas
        class="lg:flex min-h-0" :class="{ 'hidden': isMobile && mobileView !== 'canvas', 'flex flex-1 w-full h-full min-h-0': isMobile && mobileView === 'canvas' }"
        @mouseenter="hoveredSection = 'canvas'"
      />
      <InspectorPanel
        class="lg:flex min-h-0" :class="{ 'hidden': isMobile && mobileView !== 'inspector', 'flex w-full h-full min-h-0': isMobile && mobileView === 'inspector' }"
        @mouseenter="hoveredSection = 'inspector'"
      />
    </main>
    <MobileNav />
    <Toast />
  </div>
</template>
