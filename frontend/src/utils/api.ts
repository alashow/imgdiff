import type { GitImageDataResponse, GitImageEntry } from '../composables/imgDiffTypes';

const BASE_URL = "http://localhost:8080/api";

export interface LocalImageInfo {
    name: string;
    path: string;
    size: number;
}

async function get(endpoint: string, params: Record<string, string> = {}) {
    const url = new URL(`${BASE_URL}${endpoint}`);
    Object.keys(params).forEach(key => url.searchParams.append(key, params[key]));
    const response = await fetch(url.toString());
    if (!response.ok) {
        throw new Error(`API request failed: ${response.statusText}`);
    }
    return response.json();
}

export const api = {
    getModifiedImages(repoPath: string): Promise<GitImageEntry[]> {
        return get("/git/modified-images", { repoPath });
    },
    getImageData(repoPath: string, filePath: string): Promise<GitImageDataResponse> {
        return get("/git/image-data", { repoPath, filePath });
    },
    prefetchImages(repoPath: string): Promise<{ started: boolean }> {
        return get("/git/prefetch", { repoPath });
    },
    pickRepositoryFolder(): Promise<{ path: string | null }> {
        return get("/system/pick-repo-folder");
    },
    pickManualFolder(channel: 'A' | 'B'): Promise<{ path: string | null }> {
        return get("/system/pick-manual-folder", { channel });
    },
    listFolderImages(folderPath: string): Promise<LocalImageInfo[]> {
        return get("/system/list-folder-images", { folderPath });
    },
    pickManualImage(channel: 'A' | 'B'): Promise<Partial<LocalImageInfo> & { path: string | null }> {
        return get("/system/pick-manual-image", { channel });
    },
    getLocalImageUrl(path: string): string {
        const url = new URL(`${BASE_URL}/system/local-image`);
        url.searchParams.set('path', path);
        return url.toString();
    },
    revealInFinder(repoPath: string, filePath: string): Promise<{ ok: boolean }> {
        return get("/system/reveal-in-finder", { repoPath, filePath });
    },
};
