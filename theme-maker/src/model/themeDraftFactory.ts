import { DEFAULT_DRAFT, type ThemeDraft } from "./themeDraft";
import { generateThemeId } from "./themeId";

/**
 * Creates a new theme draft with a freshly generated unique ID.
 *
 * This is the production entry point for "new theme" creation:
 * `FormController` calls this in its constructor and in `newTheme()`.
 *
 * `DEFAULT_DRAFT` remains an identity-less template (id = "") so that
 * model-layer tests and validators can still assert that an empty ID
 * is rejected.  Only this factory assigns a real unique identity.
 *
 * The deep copy via `JSON.parse(JSON.stringify(...))` ensures nested
 * objects (surfaces, colors, fonts, navigation) are not shared between
 * sessions.
 */
export function createNewThemeDraft(): ThemeDraft {
  const draft = JSON.parse(JSON.stringify(DEFAULT_DRAFT)) as ThemeDraft;
  draft.id = generateThemeId();
  return draft;
}
