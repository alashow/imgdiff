import type { ManualPair } from './imgDiffTypes';

export interface ManualImageSource {
  name: string;
  size: number;
  url: string;
  file?: File | null;
}

export interface LocalImageInfoLike {
  name: string;
  path: string;
  size: number;
}

export function makeObjectUrl(file: File) {
  return URL.createObjectURL(file);
}

export function revokeObjectUrl(url: string) {
  if (url?.startsWith('blob:')) URL.revokeObjectURL(url);
}

export function toSourceFromFile(file: File): ManualImageSource {
  return {
    name: file.name,
    size: file.size,
    url: makeObjectUrl(file),
    file,
  };
}

export function toSourceFromLocalImage(
  info: LocalImageInfoLike,
  resolveUrl: (path: string) => string,
): ManualImageSource {
  return {
    name: info.name,
    size: info.size,
    url: resolveUrl(info.path),
  };
}

export function toSourceFromManualPair(pair: ManualPair | undefined, channel: 'A' | 'B'): ManualImageSource | null {
  if (!pair) return null;

  if (channel === 'A') {
    if (pair.fileA) return toSourceFromFile(pair.fileA);
    if (!pair.urlA) return null;
    return { name: pair.name, size: pair.sizeA, url: pair.urlA, file: pair.fileA };
  }

  if (pair.fileB) return toSourceFromFile(pair.fileB);
  if (!pair.urlB) return null;
  return { name: pair.name, size: pair.sizeB, url: pair.urlB, file: pair.fileB };
}

export function clearSourceMap(folder: Map<string, ManualImageSource>) {
  folder.forEach((source) => revokeObjectUrl(source.url));
  folder.clear();
}

export function revokeManualPairUrls(pair: ManualPair) {
  revokeObjectUrl(pair.urlA);
  revokeObjectUrl(pair.urlB);
}

export function getDroppedFiles(event: DragEvent) {
  const dt = event.dataTransfer;
  if (!dt) return { folders: [] as any[], files: [] as File[] };

  const items = Array.from(dt.items || []);
  const folders: any[] = [];
  const files: File[] = [];

  items.forEach(item => {
    const entry = (item as any).webkitGetAsEntry?.();
    if (entry?.isDirectory) {
      folders.push(entry);
      return;
    }

    const file = item.kind === 'file' ? item.getAsFile() : null;
    if (file && file.type.startsWith('image/')) files.push(file);
  });

  if (!folders.length && !files.length) {
    Array.from(dt.files || []).forEach(file => {
      if (file.type.startsWith('image/')) files.push(file);
    });
  }

  return { folders, files };
}

export async function traverseDirectory(dirEntry: any, map: Map<string, ManualImageSource>) {
  const reader = dirEntry.createReader();
  const readEntries = () => new Promise<any[]>((resolve, reject) => reader.readEntries(resolve, reject));

  let entries: any[];
  do {
    entries = await readEntries();
    for (const entry of entries) {
      if (entry.isFile) {
        await new Promise<void>(resolve => {
          entry.file((file: File) => {
            if (file.type.startsWith('image/')) {
              map.set(file.name, toSourceFromFile(file));
            }
            resolve();
          });
        });
      } else if (entry.isDirectory) {
        await traverseDirectory(entry, map);
      }
    }
  } while (entries.length > 0);
}

