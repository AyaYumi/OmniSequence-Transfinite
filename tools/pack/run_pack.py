"""Launch installed Forge-pack verification in a fresh build directory (Windows)."""
from pathlib import Path
import argparse
import ctypes
import json
import os
import re
import shutil
import subprocess
import time
import zipfile


def windows_arguments(command):
    shell = ctypes.windll.shell32
    shell.CommandLineToArgvW.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_int)]
    shell.CommandLineToArgvW.restype = ctypes.POINTER(ctypes.c_wchar_p)
    count = ctypes.c_int()
    pointer = shell.CommandLineToArgvW(command, ctypes.byref(count))
    if not pointer:
        raise RuntimeError("Cannot parse launcher command")
    try:
        return [pointer[i] for i in range(count.value)]
    finally:
        free = ctypes.windll.kernel32.LocalFree
        free.argtypes = [ctypes.c_void_p]
        free(pointer)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack-dir", required=True, type=Path)
    parser.add_argument("--launch-bat", required=True, type=Path)
    parser.add_argument("--applied-jar", required=True, type=Path)
    parser.add_argument("--run-name", default="pack-verification-" + time.strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--tests", default="", help="Optional lowercase test-name substring")
    parser.add_argument("--visual", action="store_true", help="Run Forge lens, motion and JEI visual regressions after menus")
    parser.add_argument("--menus-only", action="store_true", help="Stop after menu and bookmark verification")
    options = parser.parse_args()
    if os.name != "nt":
        parser.error("This launcher reads a Windows launcher batch file")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", options.run_name):
        parser.error("run-name must contain only letters, numbers, underscore or hyphen")
    if not re.fullmatch(r"[a-z0-9_]*", options.tests):
        parser.error("tests must be a lowercase test-name substring")
    root = Path(__file__).resolve().parents[2]
    pack = options.pack_dir.resolve()
    run = (root / "build" / options.run_name).resolve()
    if run.parent != (root / "build").resolve() or run.exists():
        parser.error("Verification requires a NEW directory directly inside build")
    release = root / "build/libs/omnisequence-transfinite-2.0.7-forge.jar"
    fixture = root / "build/libs/omni-pack-verification-1.jar"
    for path in [release, fixture, options.applied_jar, options.launch_bat, pack / "mods"]:
        if not path.exists():
            parser.error("Missing required input: " + str(path))
    launch = options.launch_bat.read_text(encoding="utf-8-sig", errors="replace")
    line = next((line for line in launch.splitlines() if re.search(r'"[^"\n]*javaw?\.exe"', line)), None)
    if line is None:
        parser.error("No Java launch command found")
    original = windows_arguments(line)
    if "--gameDir" not in original or Path(original[original.index("--gameDir") + 1]).resolve() != pack:
        parser.error("Launcher command must belong to the selected pack")
    mods = run / "mods"
    mods.mkdir(parents=True)
    for source in (pack / "mods").glob("*.jar"):
        if not source.name.startswith(("omnisequence-transfinite-", "appliedenhancements-")):
            shutil.copy2(source, mods / source.name)
    shutil.copy2(options.applied_jar, mods / "appliedenhancements-1.1.0-forge.jar")
    # Fixtures must share the production module and inheritance context during reobf.
    with zipfile.ZipFile(release) as main_jar, zipfile.ZipFile(fixture) as tests, \
            zipfile.ZipFile(mods / "omni-pack-test.jar", "w", zipfile.ZIP_DEFLATED) as combined:
        test_names = set(tests.namelist())
        main_names = set(main_jar.namelist())
        for entry in main_jar.infolist():
            combined.writestr(entry, tests.read(entry.filename) if entry.filename in test_names else main_jar.read(entry.filename))
        for entry in tests.infolist():
            if entry.filename not in main_names:
                combined.writestr(entry, tests.read(entry.filename))
    for directory in ["config", "defaultconfigs", "kubejs", "scripts"]:
        if (pack / directory).exists():
            shutil.copytree(pack / directory, run / directory)
    if options.visual:
        (run / "options.txt").write_text("lang:zh_cn\nguiScale:2\nrenderDistance:24\n", encoding="utf-8")
    replacements = {
        "--gameDir": str(run), "--username": "CompatibilityTest",
        "--uuid": "00000000000000000000000000000001", "--accessToken": "0",
        "--clientId": "0", "--xuid": "0", "--userType": "legacy", "--userProperties": "{}",
        "--version": "Isolated Forge verification", "--width": "854", "--height": "480",
    }
    removed = {"--server", "--port", "--quickPlayPath", "--quickPlaySingleplayer",
               "--quickPlayMultiplayer", "--quickPlayRealms", "--profileProperties"}
    args = []
    index = 0
    while index < len(original):
        value = original[index]
        if value in replacements:
            args.extend([value, replacements[value]])
            index += 2
        elif value in removed:
            index += 2
        else:
            args.append("-Xmx8G" if value.startswith("-Xmx") else "-Xms1G" if value.startswith("-Xms") else value)
            index += 1
    args.insert(1, "-Domni.packVerification=true")
    if options.visual:
        args.insert(1, "-Domni.visualVerification=true")
        for flag, value in [("--width", "1700"), ("--height", "900")]:
            args[args.index(flag) + 1] = value
    if options.menus_only:
        args.insert(1, "-Domni.menusOnlyVerification=true")
    if options.tests:
        args.insert(1, "-Domni.packTestFilter=" + options.tests)
    if "--gameDir" not in args or args[args.index("--gameDir") + 1] != str(run) or (run / "saves").exists():
        raise RuntimeError("Unsafe verification directory")
    log_path = root / "build" / (options.run_name + ".log")
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = 0
    with log_path.open("wb") as log:
        process = subprocess.Popen(args, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                                   startupinfo=startup, creationflags=subprocess.CREATE_NO_WINDOW)
    state = {"pid": process.pid, "run": str(run), "started": time.time(), "tests": options.tests}
    (root / "build" / (options.run_name + "-state.json")).write_text(json.dumps(state), encoding="utf-8")
    print("Started isolated client PID", process.pid, "with", len(list(mods.glob("*.jar"))), "mod JARs.")
    print("Log:", log_path)
    print("Account arguments replaced; no player saves copied.")


if __name__ == "__main__":
    main()
