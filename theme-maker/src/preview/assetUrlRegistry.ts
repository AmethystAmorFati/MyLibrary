export interface ObjectUrlAdapter {
  create(file: Blob): string;
  revoke(url: string): void;
}

const browserAdapter: ObjectUrlAdapter = {
  create: (file) => URL.createObjectURL(file),
  revoke: (url) => URL.revokeObjectURL(url)
};

export class AssetUrlRegistry {
  private readonly urls = new Map<string, string>();

  constructor(private readonly adapter: ObjectUrlAdapter = browserAdapter) {}

  replace(key: string, file: Blob): string {
    this.release(key);
    const url = this.adapter.create(file);
    this.urls.set(key, url);
    return url;
  }

  get(key: string): string | undefined {
    return this.urls.get(key);
  }

  release(key: string): void {
    const current = this.urls.get(key);
    if (!current) return;
    this.adapter.revoke(current);
    this.urls.delete(key);
  }

  releaseAll(): void {
    for (const url of this.urls.values()) this.adapter.revoke(url);
    this.urls.clear();
  }
}
