"""Validate publishable repository files and current documentation."""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
properties = dict(re.findall(r"^([a-z_]+)=(.*)$", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M))
omni = properties["mod_id"] == "molecularmanipulator"
allowed_docs = ({"README.md", "matter-research-api.md", "matter-research-api.zh-CN.md",
                 "omni-batch-provider-api.md", "omni-batch-provider-api.zh-CN.md", "omni-exact-provider-api.md"}
                if omni else {"README.md", "API_INTEGRATION.md", "API_INTEGRATION_ZH.md", "BATCH_EXECUTION_API.md",
                              "EXACT_CRAFTING_API.md", "SMART_DOUBLING_API.md"})
paths = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
                                cwd=ROOT).decode("utf-8").split("\0")
failures = []
for source in (ROOT / 'docs').rglob('*'):
    if source.is_file() and (source.parent != ROOT / 'docs' or source.name not in allowed_docs):
        failures.append(f"{source.relative_to(ROOT)}: not public API documentation")
for relative in filter(None, paths):
    source = ROOT / relative
    if not source.is_file():
        continue  # A tracked deletion is valid before committing.
    lower = relative.lower()
    parts = Path(lower).parts
    if (any(p in {"build", ".gradle", "logs", "run", "runs", "screenshots", "output", "test-results",
                  ".idea", ".vscode", ".claude", ".worktrees", "__pycache__", "codex-backups"} for p in parts)
            or source.suffix.lower() in {".log", ".tmp", ".bak", ".class", ".zip", ".hprof", ".jfr", ".sparkprofile"}
            or (source.suffix.lower() == ".jar" and relative != "gradle/wrapper/gradle-wrapper.jar")
            or lower.startswith(("tools/client/", "tools/pack/", "tools/ui/"))
            or source.name in {"VERSION_SYNC.md", "GhostMatterClientChecks.java",
                               "GravityWalkingClientChecks.java", "OuterWildsModelChecks.java"}
            or source.name.lower() in {"desktop.ini", "thumbs.db", ".ds_store"}):
        failures.append(f"{relative}: temporary/generated resource")
    if relative.startswith("docs/") and (source.parent != ROOT / "docs" or source.name not in allowed_docs):
        failures.append(f"{relative}: not public API documentation")
    if source.suffix.lower() in {".json", ".mcmeta"}:
        try:
            json.loads(source.read_text(encoding="utf-8"))
        except (ValueError, UnicodeError) as error:
            failures.append(f"{relative}: {error}")
    if source.suffix.lower() == ".md":
        content = source.read_text(encoding="utf-8")
        for target in re.findall(r"\]\(([^)]+)\)", content):
            target = target.strip("<>").split("#", 1)[0]
            if target and not re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", target) and not (source.parent / target).exists():
                failures.append(f"{relative}: missing link {target}")
        if re.search(r"(?:[CD]:[/\\](?:Users|Project)|/home/[^ /]+/)", content):
            failures.append(f"{relative}: machine-specific path")
        for target in re.findall(r'<(?:img|a)\b[^>]*(?:src|href)="([^"]+)"', content):
            if not re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", target) and not (source.parent / target).exists():
                failures.append(f"{relative}: missing HTML link {target}")
        if relative.startswith("docs/") or (source.name.startswith("README") and source.parent == ROOT):
            if properties["mod_version"] not in content[:1800]:
                failures.append(f"{relative}: current mod version missing from introduction")
            if properties["minecraft_version"] == "1.20.1" and re.search(r"\b(?:1\.21\.1|19\.2\.1[78]|net\.neoforged)\b", content):
                failures.append(f"{relative}: NeoForge version/API text in Forge documentation")
            if relative.startswith("docs/") and properties["minecraft_version"] == "1.20.1" and re.search(
                    r"data/<namespace>/recipe/|RecipeHolder<|ResourceLocation\.parse\(|save\(serverLevel\.registryAccess\(\)\)", content):
                failures.append(f"{relative}: newer-platform recipe path or Java signature in Forge API documentation")
            if properties["minecraft_version"] not in content[:2200]:
                failures.append(f"{relative}: current Minecraft version missing from introduction")
if failures:
    raise SystemExit("\n".join(failures))
print(f"Repository files, resource JSON and documentation validated for {properties['mod_version']}")
