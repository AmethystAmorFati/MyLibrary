import type { FontSlot } from "../model/themeManifest";
import { readBlobAsArrayBuffer } from "../files/readBlobAsArrayBuffer";

export class FontPreviewLoader {
  private readonly loaded = new Map<FontSlot, FontFace>();

  async replace(slot: FontSlot, file: File): Promise<string> {
    this.clear(slot);
    const family = `MyLibraryPreview${slot}-${crypto.randomUUID()}`;
    const face = new FontFace(family, await readBlobAsArrayBuffer(file));
    const loaded = await face.load();
    document.fonts.add(loaded);
    this.loaded.set(slot, loaded);
    return family;
  }

  clear(slot: FontSlot): void {
    const face = this.loaded.get(slot);
    if (!face) return;
    document.fonts.delete(face);
    this.loaded.delete(slot);
  }

  clearAll(): void {
    this.clear("A");
    this.clear("B");
  }
}
