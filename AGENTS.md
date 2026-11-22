# Repository agent instructions

## COUI UI convention

When writing UI for this project, follow `.claude/skills/coui-app/SKILL.md` and the files in
`.claude/skills/coui-app/references/` (vendored from https://github.com/suqi8/coui, Apache-2.0,
ColorOS 17 / COUI 17). The short rules from https://suqi8.github.io/coui/guide/ai apply:

1. Everything sits inside the COUI scaffold (`CoUiScaffold` in this project).
2. Colours from `CoUiTheme.colors`, text from `CoUiTheme.textStyles`; no hard-coded UI colours.
3. Settings/status layout = category title + card + card-list group + rows. The group draws dividers;
   never add a `HorizontalDivider` between rows.
4. Use the COUI component that matches the ColorOS widget, not a `Box`/`Text` look-alike.
5. Material blur needs a page backdrop and shader support; keep the opaque fallback.
6. User-facing text lives in `res/values/strings.xml` and `res/values-zh/strings.xml`; store ids,
   not display text, in state.
7. Composable parameter order: required parameters, `modifier`, defaulted parameters, trailing content.
8. A list popup shares a `Box` with the button that opens it.

### Project-specific adaptation

The app deliberately vendors the COUI theme tokens and a focused component subset into
`android-app/app/src/main/java/com/example/offlineaitranslator/ui/coui/` instead of adding a Maven
dependency: `android-app/settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, and the
original Step 1 brief forbids new dependency resolution. Therefore:

- Upstream package `io.github.suqi8.coui.kmp.*` maps to our package
  `com.example.offlineaitranslator.ui.coui` / `com.example.offlineaitranslator.theme`.
- `COUITheme.colorScheme` / `COUITheme.textStyles` map to `CoUiTheme.colors` / `CoUiTheme.textStyles`.
- `CouiPageScaffold` / `CouiPage` map to `CoUiScaffold` (a single-column page with a top bar).
- The blur module (`coui-blur`), squircle shapes, extended icons and `coui-nav` are not vendored;
  the UI keeps the opaque token-based fallback. Do not add them without an explicit owner decision.
- Android resources are used for bilingual strings (`res/values/`, `res/values-zh/`); the Compose
  Multiplatform `composeResources` plugin is not applied.
