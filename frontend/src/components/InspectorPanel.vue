<template>
  <aside id="sidebar-inspector" class="w-full lg:w-80 bg-slate-950 border-l border-slate-800 flex flex-col shrink-0 overflow-hidden h-full min-h-0">
    <!-- Inspection Header -->
    <div class="p-4 border-b border-slate-800 bg-slate-900/40 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-2">
        <svg class="text-indigo-400 w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.38 0 0 1 8 8v.5z"/></svg>
        <h3 class="text-[10px] uppercase font-bold tracking-wider text-slate-300">Analysis & Navigation</h3>
      </div>
      <div class="flex items-center gap-1 bg-slate-950 p-1 rounded-lg border border-slate-800">
        <button @click="activeInspectorTab = 'list'" :class="activeInspectorTab === 'list' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-white'" class="inspector-tab px-2.5 py-1 text-[10px] font-semibold rounded transition-all">Matches</button>
        <button @click="activeInspectorTab = 'stats'" :class="activeInspectorTab === 'stats' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-white'" class="inspector-tab px-2.5 py-1 text-[10px] font-semibold rounded transition-all">Metadata</button>
      </div>
    </div>

    <!-- Tab Content 1: File List (For Folders) -->
    <div v-if="activeInspectorTab === 'list'" class="inspector-content p-4 flex-1 overflow-hidden flex flex-col min-h-0">
      <div class="flex items-center justify-between mb-3 shrink-0">
        <span class="text-xs font-semibold text-slate-300">{{ appMode === 'git' ? 'Changed Images' : 'Paired Images' }}</span>
        <span v-if="appMode === 'git'" class="px-2 py-0.5 rounded-full bg-slate-800 border border-slate-700 text-[10px] text-indigo-400 font-bold code-font">{{ modifiedImages.length }} Files</span>
        <span v-else class="px-2 py-0.5 rounded-full bg-slate-800 border border-slate-700 text-[10px] text-indigo-400 font-bold code-font">{{ manualPairs.length }} Pairs</span>
      </div>

      <div v-if="appMode === 'git'" class="mb-3 shrink-0">
        <div class="relative">
          <svg class="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500 pointer-events-none" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input
            v-model="gitSearchQuery"
            type="text"
            placeholder="Filter changed/new files..."
            class="w-full pl-8 pr-8 py-1.5 rounded-lg border border-slate-800 bg-slate-900 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500/60 focus:border-indigo-500/60"
          />
          <button
            v-if="gitSearchQuery"
            @click="gitSearchQuery = ''"
            class="absolute right-1.5 top-1/2 -translate-y-1/2 p-1 rounded text-slate-500 hover:text-white hover:bg-slate-800/80 transition-all"
            type="button"
            aria-label="Clear file filter"
            title="Clear filter"
          >
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
          </button>
        </div>
      </div>

      <div ref="listScrollContainer" class="space-y-1.5 overflow-y-auto pr-1 no-scrollbar flex-1 min-h-0 h-0 pb-4">
        <!-- Git Mode List -->
        <div v-if="appMode === 'git'" class="space-y-2">
          <section v-if="modifiedGitRows.length" class="rounded-lg border border-slate-800/80 bg-slate-900/40 overflow-hidden">
            <button
              @click="isModifiedSectionOpen = !isModifiedSectionOpen"
              class="w-full px-2.5 py-2 flex items-center justify-between text-[11px] font-semibold text-indigo-300 hover:bg-slate-800/60 transition-colors"
              type="button"
            >
              <span>Modified ({{ modifiedGitRows.length }})</span>
              <svg class="w-3.5 h-3.5 text-slate-400 transition-transform" :class="isModifiedSectionOpen ? 'rotate-180' : ''" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
            </button>
            <div v-if="isModifiedSectionOpen" class="space-y-1.5 px-1.5 pb-1.5">
              <div v-for="row in modifiedGitRows" :key="row.file"
                :ref="el => setGitItemRef(el, row.index)"
                @click="onSelectGitImage(row.index)"
                :class="currentImageIndex === row.index ? 'bg-indigo-500/20 border-indigo-500/50 text-white' : 'bg-slate-900 border-slate-800 text-slate-400 hover:bg-slate-800'"
                class="p-2.5 rounded-lg border text-xs cursor-pointer transition-all flex items-center justify-between"
              >
                <div class="flex items-center gap-2 truncate min-w-0 flex-1">
                  <span :class="currentImageIndex === row.index ? 'text-indigo-300 border-indigo-400/40 bg-indigo-500/10' : 'text-slate-500 border-slate-700 bg-slate-950/60'" class="w-5 h-5 shrink-0 rounded-md border text-[10px] font-semibold code-font flex items-center justify-center">
                    {{ row.index + 1 }}
                  </span>
                  <span class="truncate font-medium" :title="row.file">{{ formatListFileLabel(row.file) }}</span>
                </div>
                <div class="ml-2 flex items-center gap-1.5 shrink-0">
                  <button
                    @click.stop="openGitImageMetadata(row.index)"
                    class="p-1.5 rounded-md border border-transparent text-slate-500 hover:text-white hover:border-slate-700 hover:bg-slate-950/60 transition-all"
                    title="Open metadata"
                    aria-label="Open metadata"
                  >
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 10v4"/><path d="M12 7h.01"/></svg>
                  </button>
                  <button
                    @click.stop="copyBaseName(row.file)"
                    class="p-1.5 rounded-md border border-transparent text-slate-500 hover:text-white hover:border-slate-700 hover:bg-slate-950/60 transition-all"
                    title="Copy file name"
                    aria-label="Copy file name"
                  >
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section v-if="newGitRows.length" class="rounded-lg border border-slate-800/80 bg-slate-900/40 overflow-hidden">
            <button
              @click="isNewSectionOpen = !isNewSectionOpen"
              class="w-full px-2.5 py-2 flex items-center justify-between text-[11px] font-semibold text-emerald-300 hover:bg-slate-800/60 transition-colors"
              type="button"
            >
              <span>New ({{ newGitRows.length }})</span>
              <svg class="w-3.5 h-3.5 text-slate-400 transition-transform" :class="isNewSectionOpen ? 'rotate-180' : ''" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
            </button>
            <div v-if="isNewSectionOpen" class="space-y-1.5 px-1.5 pb-1.5">
              <div v-for="row in newGitRows" :key="row.file"
                :ref="el => setGitItemRef(el, row.index)"
                @click="onSelectGitImage(row.index)"
                :class="currentImageIndex === row.index ? 'bg-emerald-500/20 border-emerald-500/50 text-white' : 'bg-slate-900 border-slate-800 text-slate-400 hover:bg-slate-800'"
                class="p-2.5 rounded-lg border text-xs cursor-pointer transition-all flex items-center justify-between"
              >
                <div class="flex items-center gap-2 truncate min-w-0 flex-1">
                  <span :class="currentImageIndex === row.index ? 'text-emerald-300 border-emerald-400/40 bg-emerald-500/10' : 'text-slate-500 border-slate-700 bg-slate-950/60'" class="w-5 h-5 shrink-0 rounded-md border text-[10px] font-semibold code-font flex items-center justify-center">
                    {{ row.index + 1 }}
                  </span>
                  <span class="truncate font-medium" :title="row.file">{{ formatListFileLabel(row.file) }}</span>
                </div>
                <div class="ml-2 flex items-center gap-1.5 shrink-0">
                  <button
                    @click.stop="openGitImageMetadata(row.index)"
                    class="p-1.5 rounded-md border border-transparent text-slate-500 hover:text-white hover:border-slate-700 hover:bg-slate-950/60 transition-all"
                    title="Open metadata"
                    aria-label="Open metadata"
                  >
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 10v4"/><path d="M12 7h.01"/></svg>
                  </button>
                  <button
                    @click.stop="copyBaseName(row.file)"
                    class="p-1.5 rounded-md border border-transparent text-slate-500 hover:text-white hover:border-slate-700 hover:bg-slate-950/60 transition-all"
                    title="Copy file name"
                    aria-label="Copy file name"
                  >
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                  </button>
                </div>
              </div>
            </div>
          </section>

          <div v-if="modifiedImages.length === 0" class="text-xs text-slate-500 italic text-center py-8">
            No changed images found. Scan a repository.
          </div>
          <div v-else-if="!newGitRows.length && !modifiedGitRows.length" class="text-xs text-slate-500 italic text-center py-8">
            No files match your filter.
          </div>
        </div>

        <!-- Manual Mode List -->
        <div v-if="appMode === 'manual'">
          <div v-for="(pair, index) in manualPairs" :key="pair.name"
            :ref="el => setManualItemRef(el, index)"
            @click="onSelectManualPair(index)"
            :class="currentManualPairIndex === index ? 'bg-indigo-500/20 border-indigo-500/50 text-white' : 'bg-slate-900 border-slate-800 text-slate-400 hover:bg-slate-800'"
            class="p-2.5 rounded-lg border text-xs cursor-pointer transition-all flex items-center justify-between"
          >
            <div class="flex items-center gap-2 truncate min-w-0 flex-1">
              <svg :class="currentManualPairIndex === index ? 'text-indigo-400' : 'text-slate-500'" class="w-3.5 h-3.5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
              <span class="truncate font-medium" :title="pair.name">{{ formatListFileLabel(pair.name) }}</span>
            </div>
            <div class="ml-2 flex items-center gap-1.5 shrink-0">
              <button
                @click.stop="openManualImageMetadata(index)"
                class="p-1.5 rounded-md border border-transparent text-slate-500 hover:text-white hover:border-slate-700 hover:bg-slate-950/60 transition-all"
                title="Open metadata"
                aria-label="Open metadata"
              >
                <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 10v4"/><path d="M12 7h.01"/></svg>
              </button>
              <button
                @click.stop="copyBaseName(pair.name)"
                class="p-1.5 rounded-md border border-transparent text-slate-500 hover:text-white hover:border-slate-700 hover:bg-slate-950/60 transition-all"
                title="Copy file name"
                aria-label="Copy file name"
              >
                <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
              </button>
            </div>
          </div>
          <div v-if="manualPairs.length === 0" class="text-xs text-slate-500 italic text-center py-8">
            No image pairs loaded.
          </div>
        </div>
      </div>
    </div>

    <!-- Tab Content 2: Metadata Stats -->
    <div v-if="activeInspectorTab === 'stats'" class="inspector-content px-4 py-5 space-y-4 overflow-y-auto no-scrollbar flex-1 min-h-0">

      <div class="space-y-2">
        <div class="text-xs text-slate-300 font-semibold rounded-lg border border-slate-800/80 bg-slate-900/60 px-2.5 py-2 whitespace-normal break-all leading-relaxed code-font">
          {{ currentPair.name || 'No image selected' }}
        </div>
        <div v-if="currentPair.name" class="flex items-center gap-1.5 flex-wrap">
          <button
            @click="copyBaseName(currentPair.name)"
            class="px-2.5 py-1.5 rounded-l-lg border border-r-0 border-slate-800 bg-slate-900 text-[10px] font-semibold text-slate-300 hover:text-white hover:border-slate-700 transition-all flex items-center gap-1"
            title="Copy file name only"
          >
            <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            Name
          </button>
          <button
            @click="copyFilePath(currentPair.name)"
            class="px-2.5 py-1.5 border border-r-0 border-slate-800 bg-slate-900 text-[10px] font-semibold text-slate-300 hover:text-white hover:border-slate-700 transition-all flex items-center gap-1"
            title="Copy relative file path"
          >
            <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            Path
          </button>
          <button
            v-if="appMode === 'git'"
            @click="copyAbsolutePath(currentPair.name)"
            class="px-2.5 py-1.5 border border-r-0 border-slate-800 bg-slate-900 text-[10px] font-semibold text-slate-300 hover:text-white hover:border-slate-700 transition-all flex items-center gap-1"
            title="Copy absolute file path"
          >
            <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            Absolute Path
          </button>
          <button
            v-if="appMode === 'git'"
            @click="openInFolder(currentPair.name)"
            class="px-2.5 py-1.5 rounded-r-lg border border-slate-800 bg-slate-900 text-[10px] font-semibold text-slate-300 hover:text-white hover:border-slate-700 transition-all flex items-center gap-1"
            title="Open containing folder"
          >
            <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
            Open
          </button>
        </div>
      </div>

      <!-- File Size Cards -->
      <div class="grid grid-cols-2 gap-2.5">
        <div class="p-3 bg-slate-900 border border-slate-800/80 rounded-xl relative overflow-hidden">
          <span class="absolute top-2 right-2 w-2 h-2 rounded-full bg-indigo-500"></span>
          <span class="text-[9px] uppercase tracking-wider text-slate-400 font-bold block">File Size A</span>
          <span class="text-sm font-bold text-slate-100 mt-1 block code-font">{{ formatFileSize(currentPair.sizeA) }}</span>
        </div>
        <div class="p-3 bg-slate-900 border border-slate-800/80 rounded-xl relative overflow-hidden">
          <span class="absolute top-2 right-2 w-2 h-2 rounded-full bg-sky-500"></span>
          <span class="text-[9px] uppercase tracking-wider text-slate-400 font-bold block">File Size B</span>
          <span class="text-sm font-bold text-slate-100 mt-1 block code-font">{{ formatFileSize(currentPair.sizeB) }}</span>
        </div>
      </div>

      <div class="p-3 bg-slate-900 border border-slate-800/80 rounded-xl flex items-center justify-between">
        <span class="text-[10px] uppercase tracking-wider text-slate-400 font-bold">File Size Diff (B - A)</span>
        <span class="text-xs font-semibold code-font" :class="fileSizeDiffClass">{{ formatFileSizeDiff(currentPair.sizeA, currentPair.sizeB) }}</span>
      </div>

      <!-- Dimensions Card -->
      <div class="bg-slate-900 border border-slate-800/80 rounded-2xl overflow-hidden shadow">
        <div class="px-4 py-2.5 border-b border-slate-800 bg-slate-950/40 flex items-center justify-between">
          <span class="text-[10px] uppercase font-bold tracking-wider text-slate-400">Dimensions</span>
          <span class="text-[10px] text-indigo-400 font-semibold flex items-center gap-1">
            <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3.5 5.5 20.5 5.5M3.5 18.5 20.5 18.5M5.5 3.5V20.5M18.5 3.5V20.5"/></svg>
            Pixels
          </span>
        </div>

        <div class="p-3.5 space-y-3">
          <div class="flex items-center justify-between text-xs border-b border-slate-800/50 pb-2">
            <span class="text-slate-400 flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5 text-indigo-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 12h12M6 9v6M18 9v6"/></svg>
              Width:
            </span>
            <div class="flex gap-4 code-font">
              <span class="text-indigo-400 text-xs font-semibold">A: {{ currentPair.widthA || '--' }}</span>
              <span class="text-sky-400 text-xs font-semibold">B: {{ currentPair.widthB || '--' }}</span>
            </div>
          </div>

          <div class="flex items-center justify-between text-xs border-b border-slate-800/50 pb-2">
            <span class="text-slate-400 flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5 text-sky-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M9 5h6M9 19h6"/></svg>
              Height:
            </span>
            <div class="flex gap-4 code-font">
              <span class="text-indigo-400 text-xs font-semibold">A: {{ currentPair.heightA || '--' }}</span>
              <span class="text-sky-400 text-xs font-semibold">B: {{ currentPair.heightB || '--' }}</span>
            </div>
          </div>

          <div class="flex items-center justify-between text-xs pb-2">
            <span class="text-slate-400 flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5 text-emerald-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="7" height="18" x="3" y="3"/><rect width="7" height="11" x="14" y="3"/></svg>
              Aspect Ratio:
            </span>
            <div class="flex gap-4 code-font">
              <span class="text-indigo-400 text-[10px] font-semibold">A: {{ getAspectRatio(currentPair.widthA, currentPair.heightA) }}</span>
              <span class="text-sky-400 text-[10px] font-semibold">B: {{ getAspectRatio(currentPair.widthB, currentPair.heightB) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import { useImgDiff } from '../composables/useImgDiff';
import { api } from '../utils/api';

const {
  appMode,
  activeInspectorTab,
  modifiedImages,
  gitImageStatuses,
  currentImageIndex,
  selectGitImage,
  manualPairs,
  currentManualPairIndex,
  selectManualPair,
  currentPair,
  repoPath,
  copyToClipboard,
  showToast,
  selectionChangeSource,
  setSelectionChangeSource,
} = useImgDiff();

const listScrollContainer = ref<HTMLElement | null>(null);
const gitItemEls = ref<Array<HTMLElement | null>>([]);
const manualItemEls = ref<Array<HTMLElement | null>>([]);
const isNewSectionOpen = ref(true);
const isModifiedSectionOpen = ref(true);
const gitSearchQuery = ref('');

const normalizedGitSearchQuery = computed(() => gitSearchQuery.value.trim().toLowerCase());

const gitRows = computed(() => modifiedImages.value.map((file, index) => ({
  file,
  index,
  status: gitImageStatuses.value[file] ?? 'modified',
})));

const filteredGitRows = computed(() => {
  const needle = normalizedGitSearchQuery.value;
  if (!needle) return gitRows.value;
  return gitRows.value.filter(row => row.file.toLowerCase().includes(needle));
});

const newGitRows = computed(() => filteredGitRows.value.filter(row => row.status === 'new'));
const modifiedGitRows = computed(() => filteredGitRows.value.filter(row => row.status !== 'new'));

function unwrapTemplateRefEl(el: Element | ComponentPublicInstance | null): HTMLElement | null {
  if (!el) return null;
  if (el instanceof HTMLElement) return el;
  const maybeEl = (el as ComponentPublicInstance).$el;
  return maybeEl instanceof HTMLElement ? maybeEl : null;
}

function setGitItemRef(el: Element | ComponentPublicInstance | null, index: number) {
  gitItemEls.value[index] = unwrapTemplateRefEl(el);
}

function setManualItemRef(el: Element | ComponentPublicInstance | null, index: number) {
  manualItemEls.value[index] = unwrapTemplateRefEl(el);
}

function onSelectGitImage(index: number) {
  setSelectionChangeSource('pointer');
  selectGitImage(index);
}

function onSelectManualPair(index: number) {
  setSelectionChangeSource('pointer');
  selectManualPair(index);
}

function scrollSelectedIntoView() {
  if (selectionChangeSource.value !== 'keyboard') return;
  if (activeInspectorTab.value !== 'list') return;
  const el = appMode.value === 'git'
    ? gitItemEls.value[currentImageIndex.value]
    : manualItemEls.value[currentManualPairIndex.value];
  if (el) {
    el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }
  selectionChangeSource.value = 'program';
}

watch([currentImageIndex, currentManualPairIndex, appMode, activeInspectorTab], async () => {
  if (selectionChangeSource.value !== 'keyboard') return;
  await nextTick();
  if (!listScrollContainer.value) return;
  scrollSelectedIntoView();
});

watch(currentImageIndex, index => {
  if (appMode.value !== 'git' || index < 0) return;
  const file = modifiedImages.value[index];
  if (gitImageStatuses.value[file] === 'new') {
    isNewSectionOpen.value = true;
  } else {
    isModifiedSectionOpen.value = true;
  }
});

function openGitImageMetadata(index: number) {
  setSelectionChangeSource('pointer');
  selectGitImage(index);
  activeInspectorTab.value = 'stats';
}

function openManualImageMetadata(index: number) {
  setSelectionChangeSource('pointer');
  selectManualPair(index);
  activeInspectorTab.value = 'stats';
}

function copyBaseName(filePath: string) {
  const name = filePath.split('/').pop() ?? filePath;
  copyToClipboard(name, 'File name');
}

function formatListFileLabel(filePath: string, maxChars = 52): string {
  if (filePath.length <= maxChars) return filePath;
  return `...${filePath.slice(-(maxChars - 3))}`;
}

function copyFilePath(filePath: string) {
  copyToClipboard(filePath, 'File path');
}

function copyAbsolutePath(filePath: string) {
  if (appMode.value !== 'git') {
    showToast('Absolute path is only available in Git mode.');
    return;
  }
  const base = repoPath.value?.trim();
  if (!base) {
    showToast('Repository path is empty.');
    return;
  }
  const normalizedBase = base.replace(/[\\/]+$/, '');
  const normalizedFile = filePath.replace(/^[/\\]+/, '');
  const absolutePath = `${normalizedBase}/${normalizedFile}`;
  copyToClipboard(absolutePath, 'Absolute path');
}

async function openInFolder(filePath: string) {
  if (appMode.value !== 'git') {
    showToast('Open in folder is only available in Git mode.');
    return;
  }
  try {
    await api.revealInFinder(repoPath.value, filePath);
  } catch {
    showToast('Could not open folder.');
  }
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '--';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatSignedFileSize(bytes: number): string {
  const sign = bytes > 0 ? '+' : bytes < 0 ? '-' : '';
  return `${sign}${formatFileSize(Math.abs(bytes))}`;
}

function formatFileSizeDiff(sizeA: number, sizeB: number): string {
  if (!sizeA && !sizeB) return '--';
  const diff = sizeB - sizeA;
  const signed = formatSignedFileSize(diff);
  if (!sizeA) return `${signed} (${sizeB > 0 ? '+inf' : '0.0'}%)`;
  const pct = (diff / sizeA) * 100;
  const pctSign = pct > 0 ? '+' : '';
  return `${signed} (${pctSign}${pct.toFixed(1)}%)`;
}

const fileSizeDiffClass = computed(() => {
  const diff = currentPair.sizeB - currentPair.sizeA;
  if (diff > 0) return 'text-emerald-400';
  if (diff < 0) return 'text-rose-400';
  return 'text-slate-300';
});

function getAspectRatio(width: number, height: number): string {
  if (!width || !height) return '--';
  return (width / height).toFixed(2);
}
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.code-font {
  font-family: 'Fira Code', monospace;
}
</style>

