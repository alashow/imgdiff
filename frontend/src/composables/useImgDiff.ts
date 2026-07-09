import { ref, reactive } from 'vue';
import { api } from '../utils/api';
import type {
  AppMode,
  BackdropMode,
  CompareMode,
  CurrentPair,
  GitChangeType,
  InspectorTab,
  ManualPair,
  MobileView,
  OverlayBlend,
  ToggleFrame,
} from './imgDiffTypes';
import { IS_STANDALONE } from './runtimeFlags';
import {
  clearSourceMap,
  getDroppedFiles,
  revokeManualPairUrls,
  toSourceFromFile,
  toSourceFromLocalImage,
  toSourceFromManualPair,
  traverseDirectory,
  type ManualImageSource,
} from './manualSources';

// --- Global Reactive State ---
const appMode = ref<AppMode>(IS_STANDALONE ? 'manual' : 'git');
const repoPath = ref('');
const modifiedImages = ref<string[]>([]);
const gitImageStatuses = ref<Record<string, GitChangeType>>({});
const currentImageIndex = ref(-1);

const manualPairs = ref<ManualPair[]>([]);
const currentManualPairIndex = ref(-1);
const manualFolderA = ref<Map<string, ManualImageSource>>(new Map());
const manualFolderB = ref<Map<string, ManualImageSource>>(new Map());

const currentPair = reactive<CurrentPair>({
  name: '',
  urlA: '',
  urlB: '',
  sizeA: 0,
  sizeB: 0,
  widthA: 0,
  heightA: 0,
  widthB: 0,
  heightB: 0,
});

// View preferences
const compareMode = ref<CompareMode>('slider');
const backdropMode = ref<BackdropMode>('grid-light');
const zoom = ref(1.0);
const panX = ref(0);
const panY = ref(0);
const sliderPos = ref(50);
const scaleMatchActive = ref(true);
const isDragLocked = ref(true);
const activeInspectorTab = ref<InspectorTab>('list');
const mobileView = ref<MobileView>('canvas');
const selectionChangeSource = ref<'keyboard' | 'pointer' | 'program'>('program');

const PREFS_KEY = 'imgdiff_prefs';
function loadPrefs() {
  try {
    const saved = JSON.parse(localStorage.getItem(PREFS_KEY) || '{}');
    if (saved.backdropMode) backdropMode.value = saved.backdropMode as BackdropMode;
  } catch {}
}
function savePrefs() {
  try { localStorage.setItem(PREFS_KEY, JSON.stringify({ backdropMode: backdropMode.value })); } catch {}
}
loadPrefs();

// Alignment offsets
const alignmentUnlocked = ref(false);
const alignOffsetX = ref(0);
const alignOffsetY = ref(0);

// Overlay preferences
const overlayBlend = ref<OverlayBlend>('difference');
const overlayOpacityB = ref(0.5);

// Drag/drop overlay state
const isDropActive = ref(false);
const dropMessage = ref('Drop folders or images');
let dropDepth = 0;

// Flicker/Toggle preferences
const toggleState = ref<ToggleFrame>('A');
const flickerSpeed = ref(500);
const isFlickering = ref(false);
let flickerInterval: ReturnType<typeof setInterval> | null = null;

// Toast notifications
const toastMsg = ref('');
const showToastActive = ref(false);
const isImageLoading = ref(false);
let gitImageRequestSeq = 0;

// Recent repo paths (persisted in localStorage)
const RECENT_PATHS_KEY = 'imgdiff_recent_repos';
const MAX_RECENT_PATHS = 3;
function loadRecentPaths(): string[] {
  try {
    const list = JSON.parse(localStorage.getItem(RECENT_PATHS_KEY) || '[]');
    if (!Array.isArray(list)) return [];
    const trimmed = list.slice(0, MAX_RECENT_PATHS);
    if (trimmed.length !== list.length) {
      localStorage.setItem(RECENT_PATHS_KEY, JSON.stringify(trimmed));
    }
    return trimmed;
  } catch {
    return [];
  }
}
function saveRecentPath(path: string) {
  const list = loadRecentPaths().filter(p => p !== path);
  list.unshift(path);
  try { localStorage.setItem(RECENT_PATHS_KEY, JSON.stringify(list.slice(0, MAX_RECENT_PATHS))); } catch {}
}

function createImageDimensions(url: string): Promise<{ width: number; height: number }> {
  return new Promise(resolve => {
    if (!url) {
      resolve({ width: 0, height: 0 });
      return;
    }
    const img = new Image();
    img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight });
    img.onerror = () => resolve({ width: 0, height: 0 });
    img.src = url;
  });
}

async function enrichPairDimensions(pair: ManualPair) {
  if (pair.urlA) {
    const dimsA = await createImageDimensions(pair.urlA);
    pair.widthA = dimsA.width;
    pair.heightA = dimsA.height;
  }
  if (pair.urlB) {
    const dimsB = await createImageDimensions(pair.urlB);
    pair.widthB = dimsB.width;
    pair.heightB = dimsB.height;
  }
}

function syncCurrentPairFromManual(pair?: ManualPair) {
  if (!pair) {
    currentPair.name = '';
    currentPair.urlA = '';
    currentPair.urlB = '';
    currentPair.sizeA = 0;
    currentPair.sizeB = 0;
    currentPair.widthA = 0;
    currentPair.heightA = 0;
    currentPair.widthB = 0;
    currentPair.heightB = 0;
    return;
  }
  currentPair.name = pair.name;
  currentPair.urlA = pair.urlA;
  currentPair.urlB = pair.urlB;
  currentPair.sizeA = pair.sizeA;
  currentPair.sizeB = pair.sizeB;
  currentPair.widthA = pair.widthA;
  currentPair.heightA = pair.heightA;
  currentPair.widthB = pair.widthB;
  currentPair.heightB = pair.heightB;
}

function resetViewportState() {
  zoom.value = 1.0;
  panX.value = 0;
  panY.value = 0;
}

function setDropOverlay(active: boolean, message = 'Drop folders or images') {
  isDropActive.value = active;
  if (active) {
    dropMessage.value = message;
  } else {
    dropDepth = 0;
  }
}

async function loadDroppedFolder(channel: 'A' | 'B', dirEntry: any) {
  const target = channel === 'A' ? manualFolderA.value : manualFolderB.value;
  clearSourceMap(target);
  await traverseDirectory(dirEntry, target);
}

async function setManualImagePair(sourceA: ManualImageSource | null, sourceB: ManualImageSource | null, name?: string) {
  clearSourceMap(manualFolderA.value);
  clearSourceMap(manualFolderB.value);
  manualPairs.value.forEach(revokeManualPairUrls);
  manualPairs.value = [];

  const pair: ManualPair = {
    name: name?.trim() || sourceA?.name || sourceB?.name || 'Custom Comparison',
    urlA: '',
    urlB: '',
    sizeA: 0,
    sizeB: 0,
    widthA: 0,
    heightA: 0,
    widthB: 0,
    heightB: 0,
    fileA: sourceA?.file,
    fileB: sourceB?.file,
  };

  if (sourceA) {
    pair.urlA = sourceA.url;
    pair.sizeA = sourceA.size;
  }
  if (sourceB) {
    pair.urlB = sourceB.url;
    pair.sizeB = sourceB.size;
  }

  await enrichPairDimensions(pair);
  manualPairs.value = [pair];
  currentManualPairIndex.value = 0;
  syncCurrentPairFromManual(pair);
  resetViewportState();
}

export function useImgDiff() {
  function showToast(msg: string) {
    toastMsg.value = msg;
    showToastActive.value = true;
    setTimeout(() => { showToastActive.value = false; }, 3000);
  }

  async function copyToClipboard(text: string, label = 'Text') {
    const value = text.trim();
    if (!value) {
      showToast(`No ${label.toLowerCase()} available to copy.`);
      return false;
    }

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = value;
        textarea.setAttribute('readonly', 'true');
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        const copied = document.execCommand('copy');
        document.body.removeChild(textarea);
        if (!copied) throw new Error('Copy command failed');
      }

      showToast(`${label} copied.`);
      return true;
    } catch {
      showToast(`Could not copy ${label.toLowerCase()}.`);
      return false;
    }
  }

  function resetCurrentPair() {
    currentPair.name = '';
    currentPair.urlA = '';
    currentPair.urlB = '';
    currentPair.sizeA = 0; currentPair.sizeB = 0;
    currentPair.widthA = 0; currentPair.heightA = 0;
    currentPair.widthB = 0; currentPair.heightB = 0;
  }

  function resetCurrentManualPair() {
    currentManualPairIndex.value = -1;
    manualPairs.value.forEach(revokeManualPairUrls);
    manualPairs.value = [];
  }

  function clearCurrentPairImages() {
    currentPair.urlA = '';
    currentPair.urlB = '';
    currentPair.sizeA = 0;
    currentPair.sizeB = 0;
    currentPair.widthA = 0;
    currentPair.heightA = 0;
    currentPair.widthB = 0;
    currentPair.heightB = 0;
  }

  // --- Git Mode Logic ---
  async function scanRepository(path: string) {
    if (IS_STANDALONE) {
      showToast('Git mode is unavailable in standalone manual build.');
      return;
    }
    if (!path) return;
    const normalizedPath = path.trim().replace(/\/$/, '');
    try {
      const images = await api.getModifiedImages(normalizedPath);
      modifiedImages.value = images.map(entry => entry.path);
      gitImageStatuses.value = Object.fromEntries(images.map(entry => [entry.path, entry.changeType]));
      repoPath.value = normalizedPath;
      saveRecentPath(normalizedPath);
      showToast(`Found ${images.length} changed files!`);
      if (images.length > 0) {
        // Warm backend Git/LFS cache in background to reduce per-file switch latency.
        api.prefetchImages(normalizedPath).catch(() => {});
        selectGitImage(0);
      } else {
        currentImageIndex.value = -1;
        isImageLoading.value = false;
        resetCurrentPair();
      }
    } catch (err: any) {
      showToast(`Error scanning repo: ${err.message}`);
      modifiedImages.value = [];
      gitImageStatuses.value = {};
      currentImageIndex.value = -1;
      isImageLoading.value = false;
      resetCurrentPair();
    }
  }

  async function selectGitImage(index: number) {
    if (index < 0 || index >= modifiedImages.value.length) return;
    currentImageIndex.value = index;
    const file = modifiedImages.value[index];
    currentPair.name = file;
    const requestId = ++gitImageRequestSeq;
    isImageLoading.value = true;
    // Clear stale images immediately so a failed load doesn't keep showing the previous file.
    clearCurrentPairImages();
    try {
      const data = await api.getImageData(repoPath.value, file);
      if (requestId != gitImageRequestSeq) return;
      const ext = file.split('.').pop()?.toLowerCase() || 'png';
      const mime = `image/${ext === 'jpg' ? 'jpeg' : ext}`;

      // Always assign both channels explicitly to avoid stale images when a side is unavailable.
      currentPair.urlA = data.beforeBase64 ? `data:${mime};base64,${data.beforeBase64}` : '';
      currentPair.sizeA = data.beforeBase64 ? Math.round((data.beforeBase64.length * 3) / 4) : 0;
      if (!data.beforeBase64) {
        currentPair.widthA = 0;
        currentPair.heightA = 0;
      }

      currentPair.urlB = data.afterBase64 ? `data:${mime};base64,${data.afterBase64}` : '';
      currentPair.sizeB = data.afterBase64 ? Math.round((data.afterBase64.length * 3) / 4) : 0;
      if (!data.afterBase64) {
        currentPair.widthB = 0;
        currentPair.heightB = 0;
      }

      const changeType = gitImageStatuses.value[file];
      if (changeType !== 'new' && data.beforeStatus === 'missing_in_head') {
        showToast('Previous image is not present in HEAD (likely renamed path).');
      } else if (changeType !== 'new' && data.beforeStatus === 'lfs_unavailable') {
        showToast('Previous image LFS object is unavailable locally/remotely for this path.');
      }
      resetStageView();
    } catch (err: any) {
      if (requestId != gitImageRequestSeq) return;
      showToast(`Error loading image data: ${err.message}`);
    } finally {
      if (requestId === gitImageRequestSeq) {
        isImageLoading.value = false;
      }
    }
  }

  async function pickRepositoryFolder(): Promise<string | null> {
    if (IS_STANDALONE) {
      showToast('Git mode is unavailable in standalone manual build.');
      return null;
    }
    try {
      const result = await api.pickRepositoryFolder();
      const pickedPath = result?.path?.trim() || '';
      if (!pickedPath) return null;
      repoPath.value = pickedPath;
      return pickedPath;
    } catch (err: any) {
      showToast(`Error opening folder picker: ${err.message}`);
      return null;
    }
  }

  // --- Manual Mode Logic ---
  function setGitMode() {
    if (IS_STANDALONE) {
      appMode.value = 'manual';
      showToast('Git mode is unavailable in standalone manual build.');
      return;
    }
    appMode.value = 'git';
    stopFlickerLoop();

    const count = modifiedImages.value.length;
    if (count === 0) {
      currentImageIndex.value = -1;
      resetCurrentPair();
      return;
    }

    const index = (currentImageIndex.value >= 0 && currentImageIndex.value < count)
      ? currentImageIndex.value
      : 0;
    void selectGitImage(index);
  }

  function setManualMode() {
    appMode.value = 'manual';
    stopFlickerLoop();

    const count = manualPairs.value.length;
    if (count === 0) {
      currentManualPairIndex.value = -1;
      resetCurrentPair();
      return;
    }

    const index = (currentManualPairIndex.value >= 0 && currentManualPairIndex.value < count)
      ? currentManualPairIndex.value
      : 0;
    selectManualPair(index);
  }

  function openFolderDialog(_channel: 'A' | 'B') {
    showToast('Use the new Folder A/B picker buttons in Manual mode.');
  }

  function openFileDialog(_channel: 'A' | 'B') {
    showToast('Use the Image A/B picker buttons in Manual mode.');
  }

  function rebuildManualPairsFromFolders() {
    manualPairs.value.forEach(revokeManualPairUrls);
    const keysA = Array.from(manualFolderA.value.keys());
    const keysB = Array.from(manualFolderB.value.keys());
    const common = keysA.filter(name => keysB.includes(name)).sort((a, b) => a.localeCompare(b));

    const nextPairs: ManualPair[] = common.map(name => {
      const sourceA = manualFolderA.value.get(name)!;
      const sourceB = manualFolderB.value.get(name)!;
      return {
        name,
        urlA: sourceA.url,
        urlB: sourceB.url,
        sizeA: sourceA.size,
        sizeB: sourceB.size,
        widthA: 0,
        heightA: 0,
        widthB: 0,
        heightB: 0,
        fileA: sourceA.file,
        fileB: sourceB.file,
      };
    });

    manualPairs.value = nextPairs;
    currentManualPairIndex.value = nextPairs.length ? 0 : -1;
    syncCurrentPairFromManual(nextPairs[0]);
  }

  async function setManualFolder(channel: 'A' | 'B', files: FileList | File[]) {
    const list = Array.from(files).filter(file => file.type.startsWith('image/')).map(toSourceFromFile);
    if (!list.length) {
      showToast('No image files found in selected folder.');
      return;
    }

    appMode.value = 'manual';

    const target = channel === 'A' ? manualFolderA.value : manualFolderB.value;
    resetCurrentManualPair();
    clearSourceMap(target);
    list.forEach(source => target.set(source.name, source));

    rebuildManualPairsFromFolders();

    if (manualPairs.value.length > 0) {
      for (const pair of manualPairs.value) await enrichPairDimensions(pair);
      syncCurrentPairFromManual(manualPairs.value[0]);
      showToast(`Found ${manualPairs.value.length} matching pairs!`);
      resetStageView();
    } else if (manualFolderA.value.size > 0 && manualFolderB.value.size > 0) {
      showToast('No files with matching names found in both folders.');
    } else {
      showToast(`Loaded ${list.length} images into Folder ${channel}.`);
    }
  }

  async function pickManualFolder(channel: 'A' | 'B') {
    if (IS_STANDALONE) {
      showToast(`Use browser folder picker for Folder ${channel}.`);
      return;
    }
    try {
      const picked = await api.pickManualFolder(channel);
      const folderPath = picked?.path?.trim() || '';
      if (!folderPath) return;

      const images = await api.listFolderImages(folderPath);
      const normalized = images
        .filter(item => !!item.path && !!item.name)
        .map(item => toSourceFromLocalImage(item, api.getLocalImageUrl));

      if (!normalized.length) {
        showToast('No image files found in selected folder.');
        return;
      }

      appMode.value = 'manual';
      const target = channel === 'A' ? manualFolderA.value : manualFolderB.value;
      resetCurrentManualPair();
      clearSourceMap(target);
      normalized.forEach(source => target.set(source.name, source));
      rebuildManualPairsFromFolders();

      if (manualPairs.value.length > 0) {
        for (const pair of manualPairs.value) await enrichPairDimensions(pair);
        syncCurrentPairFromManual(manualPairs.value[0]);
        showToast(`Found ${manualPairs.value.length} matching pairs!`);
        resetStageView();
      } else if (manualFolderA.value.size > 0 && manualFolderB.value.size > 0) {
        showToast('No files with matching names found in both folders.');
      } else {
        showToast(`Loaded ${normalized.length} images into Folder ${channel}.`);
      }
    } catch (err: any) {
      showToast(`Error opening folder picker: ${err.message}`);
    }
  }

  async function pickManualImage(channel: 'A' | 'B') {
    if (IS_STANDALONE) {
      showToast(`Use browser file picker for Image ${channel}.`);
      return;
    }
    try {
      const result = await api.pickManualImage(channel);
      const pickedPath = result?.path?.trim() || '';
      if (!pickedPath || !result.name || typeof result.size !== 'number') return;

      const source = toSourceFromLocalImage({
        name: result.name,
        path: pickedPath,
        size: result.size,
      }, api.getLocalImageUrl);

      appMode.value = 'manual';
      const activePair = manualPairs.value[currentManualPairIndex.value] || manualPairs.value[0];
      await setManualImagePair(
        channel === 'A' ? source : toSourceFromManualPair(activePair, 'A'),
        channel === 'B' ? source : toSourceFromManualPair(activePair, 'B'),
        activePair?.name || result.name,
      );
      showToast('Loaded manual image comparison.');
    } catch (err: any) {
      showToast(`Error opening image picker: ${err.message}`);
    }
  }

  async function addManualFiles(channel: 'A' | 'B', files: FileList | File[]) {
    const list = Array.from(files).filter(file => file.type.startsWith('image/')).map(toSourceFromFile);
    if (!list.length) {
      showToast('No images found in selection.');
      return;
    }

    appMode.value = 'manual';

    const activePair = manualPairs.value[currentManualPairIndex.value] || manualPairs.value[0];
    await setManualImagePair(
      channel === 'A' ? list[0] : toSourceFromManualPair(activePair, 'A'),
      channel === 'B' ? list[0] : toSourceFromManualPair(activePair, 'B'),
      activePair?.name || list[0].name,
    );
    showToast('Loaded manual image comparison.');
  }

  async function handleManualDrop(event: DragEvent) {
    event.preventDefault();
    setDropOverlay(false);

    const { folders, files } = getDroppedFiles(event);
    if (folders.length >= 2) {
      appMode.value = 'manual';
      await loadDroppedFolder('A', folders[0]);
      await loadDroppedFolder('B', folders[1]);
      rebuildManualPairsFromFolders();
      if (manualPairs.value.length > 0) {
        for (const pair of manualPairs.value) await enrichPairDimensions(pair);
        syncCurrentPairFromManual(manualPairs.value[0]);
        showToast(`Found ${manualPairs.value.length} matching pairs!`);
      } else {
        showToast('No files with matching names found in both folders.');
      }
      return;
    }

    if (folders.length === 1) {
      appMode.value = 'manual';
      const channel = manualFolderA.value.size === 0 ? 'A' : 'B';
      await loadDroppedFolder(channel, folders[0]);
      rebuildManualPairsFromFolders();
      if (manualPairs.value.length > 0) {
        for (const pair of manualPairs.value) await enrichPairDimensions(pair);
        syncCurrentPairFromManual(manualPairs.value[0]);
        showToast(`Found ${manualPairs.value.length} matching pairs!`);
      } else {
        showToast(`Loaded ${channel === 'A' ? manualFolderA.value.size : manualFolderB.value.size} images into Folder ${channel}.`);
      }
      return;
    }

    if (files.length >= 2) {
      appMode.value = 'manual';
      await setManualImagePair(toSourceFromFile(files[0]), toSourceFromFile(files[1]), `${files[0].name}`);
      showToast('Loaded both images successfully!');
      return;
    }

    if (files.length === 1) {
      appMode.value = 'manual';
      const file = files[0];
      const current = manualPairs.value[0];
      if (current?.urlA && !current.urlB) {
        await setManualImagePair(
          toSourceFromManualPair(current, 'A'),
          toSourceFromFile(file),
          current.name || file.name
        );
      } else if (current?.urlB && !current.urlA) {
        await setManualImagePair(
          toSourceFromFile(file),
          toSourceFromManualPair(current, 'B'),
          current.name || file.name
        );
      } else {
        await setManualImagePair(toSourceFromFile(file), null, file.name);
      }
      showToast('Loaded manual image comparison.');
    }
  }

  function handleManualDragEnter() {
    dropDepth += 1;
    setDropOverlay(true);
  }

  function handleManualDragLeave() {
    dropDepth = Math.max(0, dropDepth - 1);
    if (dropDepth === 0) setDropOverlay(false);
  }

  function handleManualDragOver() {
    if (!isDropActive.value) setDropOverlay(true);
  }

  function selectManualPair(index: number) {
    if (index < 0 || index >= manualPairs.value.length) return;
    currentManualPairIndex.value = index;
    syncCurrentPairFromManual(manualPairs.value[index]);
    resetStageView();
  }

  function setSelectionChangeSource(source: 'keyboard' | 'pointer' | 'program') {
    selectionChangeSource.value = source;
  }

  // --- View Manipulation Actions ---
  function resetStageView() {
    resetViewportState();
  }

  function toggleScaleMatch() {
    scaleMatchActive.value = !scaleMatchActive.value;
  }

  function startFlickerLoop() {
    if (isFlickering.value) return;
    isFlickering.value = true;
    flickerInterval = setInterval(() => {
      toggleState.value = toggleState.value === 'A' ? 'B' : 'A';
    }, flickerSpeed.value);
  }

  function stopFlickerLoop() {
    if (!isFlickering.value) return;
    isFlickering.value = false;
    if (flickerInterval !== null) {
      clearInterval(flickerInterval);
    }
    flickerInterval = null;
  }

  function toggleFlickerActive() {
    isFlickering.value ? stopFlickerLoop() : startFlickerLoop();
  }

  function setFlickerSpeed(speed: number) {
    flickerSpeed.value = speed;
    if (isFlickering.value) {
      stopFlickerLoop();
      startFlickerLoop();
    }
  }

  function updateDimensionsA(w: number, h: number) {
    currentPair.widthA = w;
    currentPair.heightA = h;
  }

  function updateDimensionsB(w: number, h: number) {
    currentPair.widthB = w;
    currentPair.heightB = h;
  }

   function toggleAlignmentLock() {
     alignmentUnlocked.value = !alignmentUnlocked.value;
     if (!alignmentUnlocked.value) {
       alignOffsetX.value = 0;
       alignOffsetY.value = 0;
     }
   }

    function setCompareMode(mode: CompareMode) {
      const didChangeMode = compareMode.value !== mode;
      compareMode.value = mode;
      if (didChangeMode) {
        resetStageView();
      }
      // Side-by-side uses a different layout, so keep fit disabled there by default.
      // Slider/overlay/toggle default to fit enabled.
      scaleMatchActive.value = mode !== 'side-by-side';
      if (mode !== 'toggle') {
        stopFlickerLoop();
      } else if (mode === 'toggle' && !isFlickering.value) {
        startFlickerLoop();
      }
    }

    function setBackdrop(theme: BackdropMode) {
      backdropMode.value = theme;
      savePrefs();
    }

   function setContextSliderVal(val: number) {
     sliderPos.value = val;
   }

   function setContextOpacityVal(val: number) {
     overlayOpacityB.value = val / 100;
   }

    function setBlendFilter(mode: OverlayBlend) {
     overlayBlend.value = mode;
   }

   function setAlignmentOffset(axis: 'x' | 'y', value: number) {
     if (axis === 'x') alignOffsetX.value = value;
     else alignOffsetY.value = value;
   }

    function setInspectorTab(tab: InspectorTab) {
     activeInspectorTab.value = tab;
   }

    function setMobileView(view: MobileView) {
     mobileView.value = view;
   }

   function toggleDragLock() {
      isDragLocked.value = !isDragLocked.value;
    }

   function navigateFiles(delta: 1 | -1, source: 'keyboard' | 'pointer' | 'program' = 'program') {
      selectionChangeSource.value = source;
      if (appMode.value === 'manual') {
        const count = manualPairs.value.length;
        if (count === 0) return;
        const current = currentManualPairIndex.value >= 0 ? currentManualPairIndex.value : 0;
        const next = ((current + delta) + count) % count;
        selectManualPair(next);
        return;
      }

      const count = modifiedImages.value.length;
      if (count === 0) return;
      const current = currentImageIndex.value >= 0 ? currentImageIndex.value : 0;
      const next = ((current + delta) + count) % count;
      selectGitImage(next);
    }

   return {
    isStandalone: IS_STANDALONE,
    appMode,
    repoPath,
    modifiedImages,
    gitImageStatuses,
    currentImageIndex,
    manualPairs,
    currentManualPairIndex,
    currentPair,
    compareMode,
    backdropMode,
    zoom,
    panX,
    panY,
    sliderPos,
    scaleMatchActive,
    isDragLocked,
    activeInspectorTab,
    mobileView,
    selectionChangeSource,
    alignmentUnlocked,
    alignOffsetX,
    alignOffsetY,
    overlayBlend,
    overlayOpacityB,
    isDropActive,
    dropMessage,
    toggleState,
    flickerSpeed,
    isFlickering,
    toastMsg,
    showToastActive,
    isImageLoading,
    
    showToast,
    copyToClipboard,
    scanRepository,
    pickRepositoryFolder,
    selectGitImage,
    setGitMode,
    setManualMode,
    setManualFolder,
    pickManualFolder,
    addManualFiles,
    pickManualImage,
    handleManualDrop,
    handleManualDragEnter,
    handleManualDragLeave,
    handleManualDragOver,
    openFolderDialog,
    openFileDialog,
    selectManualPair,
    resetStageView,
    toggleScaleMatch,
    toggleDragLock,
    toggleFlickerActive,
    setFlickerSpeed,
    updateDimensionsA,
    updateDimensionsB,
    toggleAlignmentLock,
    setCompareMode,
    setBackdrop,
    setContextSliderVal,
    setContextOpacityVal,
    setBlendFilter,
    setAlignmentOffset,
    setInspectorTab,
    setMobileView,
    setSelectionChangeSource,
    loadRecentPaths,
    navigateFiles,
  };
}

