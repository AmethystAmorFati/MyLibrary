/**
 * Generates a unique theme ID in the form `theme.<uuid>`.
 *
 * `crypto.randomUUID()` already yields a lowercase, hyphen-separated UUID,
 * so the resulting identifier satisfies `THEME_ID_PATTERN`
 * (`^[a-z0-9][a-z0-9._-]*$`) without any further transformation. The full
 * identifier (`theme.` plus the 36-character UUID) is well within the
 * 64-character maximum length.
 */
export function generateThemeId(): string {
  return `theme.${crypto.randomUUID()}`;
}
