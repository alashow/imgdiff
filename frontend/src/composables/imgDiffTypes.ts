export type AppMode = 'git' | 'manual';
export type CompareMode = 'slider' | 'overlay' | 'side-by-side' | 'toggle';
export type BackdropMode = 'grid-light' | 'grid-dark' | 'white' | 'dark';
export type InspectorTab = 'list' | 'stats';
export type MobileView = 'controls' | 'canvas' | 'inspector';
export type OverlayBlend = 'difference' | 'exclusion' | 'normal' | 'multiply' | 'screen';
export type ToggleFrame = 'A' | 'B';

export type BeforeImageStatus = 'ok' | 'missing_in_head' | 'lfs_unavailable' | 'invalid_repo';
export type GitChangeType = 'modified' | 'new';

export interface GitImageEntry {
  path: string;
  changeType: GitChangeType;
}

export interface GitImageDataResponse {
  beforeBase64: string | null;
  afterBase64: string | null;
  beforeStatus?: BeforeImageStatus;
}

export interface CurrentPair {
  name: string;
  urlA: string;
  urlB: string;
  sizeA: number;
  sizeB: number;
  widthA: number;
  heightA: number;
  widthB: number;
  heightB: number;
}

export interface ManualPair {
  name: string;
  urlA: string;
  urlB: string;
  sizeA: number;
  sizeB: number;
  widthA: number;
  heightA: number;
  widthB: number;
  heightB: number;
  fileA?: File | null;
  fileB?: File | null;
}
