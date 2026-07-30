export const MiB = 1024 * 1024;
export const KiB = 1024;

export const THEME_LIMITS = {
  schemaVersion: 1,
  maxThemeIdLength: 64,
  maxManifestStringLength: 256,
  maxFileNameLength: 128,
  maxSurfaceImages: 3,
  minImageBytes: 12,
  maxSurfaceBytes: {
    background: 12 * MiB,
    card: 8 * MiB,
    dialog: 8 * MiB
  },
  maxTotalSurfaceBytes: 24 * MiB,
  minImageSide: 16,
  maxImageSide: 8192,
  maxSurfacePixels: {
    background: 16_000_000,
    card: 8_000_000,
    dialog: 8_000_000
  },
  maxFontFiles: 2,
  minFontBytes: 28,
  maxSingleFontBytes: 32 * MiB,
  maxTotalFontBytes: 64 * MiB,
  maxNavigationImages: 8,
  minNavigationImageBytes: 12,
  maxNavigationImageBytes: 512 * KiB,
  maxTotalNavigationBytes: 2 * MiB,
  minNavigationSide: 8,
  maxNavigationSide: 1024,
  maxNavigationPixels: 262_144,
  maxNavigationAspectRatio: 4,
  maxSourceArchiveBytes: 96 * MiB,
  maxTotalUncompressedBytes: 96 * MiB,
  maxFileEntries: 15,
  maxTotalEntries: 32,
  maxManifestBytes: 256 * KiB,
  maxChecksumsBytes: 256 * KiB
} as const;

export const THEME_ID_PATTERN = /^[a-z0-9][a-z0-9._-]*$/;
export const ARGB_PATTERN = /^#[0-9A-Fa-f]{8}$/;
