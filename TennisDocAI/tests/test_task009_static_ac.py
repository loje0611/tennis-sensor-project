#!/usr/bin/env python3
"""Static Acceptance Criteria checks for TASK-009 (rename / skeleton).

Runnable without Android SDK. Does not replace Gradle verify/test/assemble.
Exit 0 only if all static checks pass.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONO = ROOT.parent
SKIP_DIR_NAMES = {"build", ".gradle", ".idea", ".git"}


def iter_text_files(base: Path):
    for p in base.rglob("*"):
        if not p.is_file():
            continue
        if any(part in SKIP_DIR_NAMES for part in p.parts):
            continue
        if p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".so", ".a", ".o", ".jar", ".apk", ".aab"}:
            continue
        try:
            yield p, p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue


def main() -> int:
    failures = []

    if not ROOT.is_dir():
        failures.append("TennisDocAI/ missing")
    if (MONO / "SwingSenseAI").exists():
        failures.append("SwingSenseAI/ still exists")

    package_hits = []
    class_hits = {k: [] for k in ("SwingSenseApplication", "SwingSenseDatabase", "SwingColorScheme")}
    db_ok = False
    # Residual-id search excludes this harness (it intentionally names the old identifiers).
    for path, text in iter_text_files(ROOT):
        rel = path.relative_to(ROOT)
        if rel.parts and rel.parts[0] == "tests":
            continue
        if "com.example.swingsenseai" in text:
            package_hits.append(str(rel))
        for k in class_hits:
            if re.search(rf"\b{k}\b", text):
                class_hits[k].append(str(rel))
        if '"swingsense.db"' in text or "'swingsense.db'" in text:
            db_ok = True

    if package_hits:
        failures.append(f"com.example.swingsenseai residual: {package_hits[:10]}")
    for k, hits in class_hits.items():
        if hits:
            failures.append(f"{k} residual: {hits[:10]}")
    if not db_ok:
        failures.append('swingsense.db string missing')

    strings = ROOT / "app/src/main/res/values/strings.xml"
    s = strings.read_text(encoding="utf-8")
    if ">TennisDoc AI<" not in s or 'name="app_name"' not in s:
        failures.append("app_name not TennisDoc AI")
    if 'name="notification_title">TennisDoc AI<' not in s.replace("\n", ""):
        # tolerant check
        if not re.search(r'name="notification_title">\s*TennisDoc AI\s*<', s):
            failures.append("notification_title not TennisDoc AI")

    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    if 'rootProject.name = "TennisDocAI"' not in settings:
        failures.append("rootProject.name not TennisDocAI")
    for mod in (
        ":app",
        ":core:ui",
        ":core:sensor",
        ":core:data",
        ":core:analysis",
        ":core:vision",
        ":feature:match",
        ":feature:history",
        ":feature:lab",
    ):
        if f'include("{mod}")' not in settings and f"include('{mod}')" not in settings:
            failures.append(f"settings missing include {mod}")

    app_bg = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if 'namespace = "io.github.loje0611.tennisdoc"' not in app_bg:
        failures.append("app namespace wrong")
    if 'applicationId = "io.github.loje0611.tennisdoc"' not in app_bg:
        failures.append("app applicationId wrong")

    skeleton = [
        "core/ui",
        "core/sensor",
        "core/data",
        "core/analysis",
        "core/vision",
        "feature/match",
        "feature/history",
        "feature/lab",
    ]
    for rel in skeleton:
        src_files = list((ROOT / rel).rglob("*.kt")) + list((ROOT / rel).rglob("*.java"))
        src_files = [p for p in src_files if "build" not in p.parts]
        if src_files:
            failures.append(f"skeleton {rel} has sources: {src_files[:5]}")

    for rel in ("core/ui", "core/sensor", "core/data", "core/analysis", "feature/match", "feature/history", "feature/lab"):
        bg = (ROOT / rel / "build.gradle.kts").read_text(encoding="utf-8")
        if re.search(r"\bcompileSdk\b|\bminSdk\b|\bcompileOptions\b", bg):
            failures.append(f"{rel}/build.gradle.kts has compileSdk/minSdk/compileOptions")

    vision_bg = (ROOT / "core/vision/build.gradle.kts").read_text(encoding="utf-8")
    if "com.android" in vision_bg or "tennisdoc.android" in vision_bg:
        failures.append("core:vision build script references Android plugins")

    ai = (ROOT / "AI_README.md").read_text(encoding="utf-8")
    if "verifyModuleDependencies" not in ai:
        failures.append("AI_README missing verifyModuleDependencies command")
    # FR-8: must describe multimodule beyond lone :app
    if ":core:" not in ai or ":feature:" not in ai:
        failures.append("AI_README does not describe :core:/:feature: multimodule layout")

    if failures:
        print("STATIC AC FAIL")
        for f in failures:
            print(" -", f)
        return 1
    print("STATIC AC PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
