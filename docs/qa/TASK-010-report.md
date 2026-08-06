# TASK-010 QA Report — Extract :core:ui (theme + shared Compose components)

**Date:** 2026-08-06T23:58:28Z  
**Target:** `TennisDocAI` (`:core:ui`, `:app` wiring)  
**Spec:** `docs/specs/TASK-010-core-ui-extraction.md` (v1)  
**Result:** **QA_PASSED**

## Run 1 (spec v1)

### Boundary Check

Inspected via `git status --short` / `git diff --name-only` (uncommitted Developer change set):

| Path category | Paths | Verdict |
|---|---|---|
| Production / build (Developer OK) | `:app` import/wiring, deleted `app/.../ui/theme/*` & `SwingLabelFormatter.kt`, new `core/ui/src/main/**`, `app/build.gradle.kts`, `core/ui/build.gradle.kts` | OK |
| Screen packages (AC-13) | `ui/history/**`, `ui/practice/**`, `ui/settings/**` | OK — import package updates only (14+/14−) |
| Test sources written by Developer | `core/ui/src/test/.../ThemeColorSchemeTest.kt`, `SwingLabelFormatterTest.kt` | **Accepted exception** — authorized by **FR-8 / AC-10 / AC-11** (spec mandates adding these unit tests). Diff is new FR-8 coverage only; no alteration of unrelated existing assertions. |
| Spec / board / turn | `docs/specs/TASK-010-*.md`, `docs/task-board.json`, `docs/turn.json` | Outside Developer write matrix for production; owned by PM / pipeline |

No boundary violation.

### Command Executed

```bash
cd TennisDocAI
./gradlew verifyModuleDependencies test assembleDebug
```

Environment notes (permitted setup only): created ignored `local.properties` (`sdk.dir` + Linux `ndk.dir`); installed Linux NDK `28.2.13676358` under `/home/keunu/Android/Sdk` because the Windows SDK NDK host was `windows-x86_64`.

**Result:** `BUILD SUCCESSFUL in 1m 35s` (exit code 0)

### Unit Test Evidence

Re-run with `--rerun-tasks` for `:core:ui:testDebugUnitTest` / `:app:testDebugUnitTest` + full command above.

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| `ThemeColorSchemeTest` (FR-8.1) | 2 | 0 | 0 |
| `SwingLabelFormatterTest` (FR-8.2) | 4 | 0 | 0 |
| Existing `:app` unit tests | 49 | 0 | 0 |
| **Total** | **55** | **0** | **0** |

### Acceptance Criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| AC-1 | 5 files exist under `:core:ui` | PASS | `ls` shows `Color.kt`, `TennisDocColorScheme.kt`, `Theme.kt`, `Type.kt`, `SwingLabelFormatter.kt` under `core/ui/src/main/...` |
| AC-2 | Same 5 absent from `:app` | PASS | `app/.../ui/theme` and `app/.../ui/SwingLabelFormatter.kt` absent |
| AC-3 | FR-2 public symbols exposed | PASS | Symbols resolve from `:core:ui` (compile + tests import `DarkSwingColors`/`LightSwingColors`; `TennisDocTheme`/`SwingTheme`/`Typography` present; app compiles against module) |
| AC-4 | No `SwingSenseAITheme` | PASS | `rg SwingSenseAITheme` over sources (excl. build) → none |
| AC-5 | `TennisDocTheme(isDarkMode, content)` | PASS | `Theme.kt` defines `@Composable fun TennisDocTheme(isDarkMode: Boolean = true, content: ...)` |
| AC-6 | `:core:ui` has no project module deps | PASS | `core/ui/build.gradle.kts` only `libs.androidx.core.ktx` + `libs.junit` |
| AC-7 | `:app` depends on `:core:ui` | PASS | `implementation(project(":core:ui"))` in `app/build.gradle.kts` |
| AC-8 | `verifyModuleDependencies test assembleDebug` green | PASS | Full command exit 0 / `BUILD SUCCESSFUL` |
| AC-9 | Existing unit tests still pass | PASS | 49 app unit tests, 0 failures |
| AC-10 | FR-8.1 color scheme test | PASS | `ThemeColorSchemeTest` 2/2 |
| AC-11 | FR-8.2 formatter test | PASS | `SwingLabelFormatterTest` 4/4 |
| AC-12 | No hard-coded versions / no rogue new libs | PASS | New deps use version catalog only (`libs.*`); no numeric version strings in `core/ui/build.gradle.kts` |
| AC-13 | history/practice/settings not moved | PASS | Diffs are import rewrites only; files remain under `app/.../ui/{history,practice,settings}` |

## Verdict

**QA_PASSED** — declared command completed; every AC has executed/search evidence.
