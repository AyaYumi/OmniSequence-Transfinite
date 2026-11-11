"""Real JEI + Sponge Mixin transformation and Java 21 linking; no Gradle or game startup.

Run from any directory: python -B /path/to/project/src/test/python/run_jei_mixin_smoke.py
Requires an existing, up-to-date build/classes/java/main and build/moddev runtime classpath.
Only the Java helpers in src/test/jei are compiled, outside Gradle's test Java source set.
JAVA_HOME selects a JDK 21; otherwise the JDK containing javac on PATH is used.
Dependencies are read from GRADLE_USER_HOME (default: ~/.gradle), never downloaded or changed.
JEI fixtures use RECIPE_LOCK_JEI_OLD_JAR / RECIPE_LOCK_JEI_INSTALLED_JAR /
RECIPE_LOCK_JEI_LATEST_JAR, or the exact-version Gradle cache entries. Default: --runtime old.
A requested missing/invalid runtime is a failure, not a skip. All generated output is replaced
under build/jei-mixin-smoke. This tests four production mixins, not the production plugin,
mod discovery, tooltip behavior, or client/world startup.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

PROJECT = Path(__file__).resolve().parents[3]
HELPERS = PROJECT / 'src/test/jei/smoke'
WORK = PROJECT / 'build/jei-mixin-smoke'
CACHE = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')).expanduser().resolve() / 'caches/modules-2/files-2.1'
MIXIN_NAMES = ['JeiRecipeLockSlotMixin', 'JeiRecipeLockLayoutMixin',
               'JeiResponsiveSlotMixin', 'JeiResponsiveRenderMixin']
RUNTIMES = {
    'old': ('19.27.0.340', 'RECIPE_LOCK_JEI_OLD_JAR'),
    'new': ('19.56.0.441', 'RECIPE_LOCK_JEI_INSTALLED_JAR'),
    'latest': ('19.70.0.469', 'RECIPE_LOCK_JEI_LATEST_JAR'),
}


def project_versions() -> dict[str, str]:
    properties = {}
    for line in (PROJECT / 'gradle.properties').read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith(('#', '!')) and '=' in line:
            key, value = line.split('=', 1)
            properties[key.strip()] = value.strip()
    for key in ['minecraft_version', 'neo_version', 'ae2_version']:
        if not properties.get(key):
            raise RuntimeError(f'Missing {key} in gradle.properties')
    return properties


def cached(coordinates: str) -> Path:
    group, artifact, version, *classifier = coordinates.split(':')
    suffix = ('-' + classifier[0]) if classifier else ''
    filename = f'{artifact}-{version}{suffix}.jar'
    found = sorted((CACHE / group / artifact / version).glob('*/' + filename))
    if len(found) != 1:
        raise RuntimeError(f'Expected one cached artifact {coordinates} under {CACHE}, found {found}. '
                           'Prepare dependencies separately; this runner never downloads or invokes Gradle.')
    return found[0]


def runtime_jar(name: str, minecraft_version: str) -> Path:
    version, env = RUNTIMES[name]
    artifact = f'jei-{minecraft_version}-neoforge'
    configured = os.environ.get(env)
    if configured is not None:
        if not configured.strip():
            raise RuntimeError(f'{env} is empty; set it to the JEI {version} JAR or unset it to use the cache')
        jar = Path(configured).expanduser().resolve()
    else:
        try:
            jar = cached(f'mezz.jei:{artifact}:{version}')
        except RuntimeError as error:
            raise RuntimeError(f'Requested runtime {name} (JEI {version}) is unavailable. '
                               f'Set {env} to an existing {artifact}-{version}.jar.\n{error}') from error
    if not jar.is_file():
        raise RuntimeError(f'Requested runtime {name} (JEI {version}) is missing: {jar}; set {env} '
                           'to an existing JAR (no runtime is downloaded or skipped)')
    try:
        with zipfile.ZipFile(jar) as archive:
            bad_entry = archive.testzip()
            if bad_entry:
                raise RuntimeError(f'JEI {version} failed its ZIP CRC check: {jar}: {bad_entry}')
            metadata = archive.read('META-INF/neoforge.mods.toml').decode('utf-8')
    except (zipfile.BadZipFile, KeyError, UnicodeError) as error:
        raise RuntimeError(f'Invalid JEI {version} fixture: {jar}; set {env}: {error}') from error
    mods = [block for block in re.findall(r'\[\[mods\]\](.*?)(?=\[\[|\Z)', metadata, re.S)
            if re.search(r'modId\s*=\s*"jei"', block)]
    if len(mods) != 1 or not re.search(r'version\s*=\s*"' + re.escape(version) + '"', mods[0]):
        raise RuntimeError(f'Expected NeoForge JEI {version} in {jar}; check {env}')
    return jar


def java_tools() -> tuple[Path, Path]:
    suffix = '.exe' if os.name == 'nt' else ''
    java_home = os.environ.get('JAVA_HOME')
    if java_home:
        javac = Path(java_home).expanduser().resolve() / 'bin' / ('javac' + suffix)
    else:
        executable = shutil.which('javac' + suffix)
        if not executable:
            raise RuntimeError('JDK 21 required: set JAVA_HOME or put its javac on PATH')
        javac = Path(executable).resolve()
    java = javac.with_name('java' + suffix)
    for tool in [javac, java]:
        if not tool.is_file():
            raise RuntimeError(f'JDK 21 tool missing: {tool}; set JAVA_HOME to a JDK 21')
        result = subprocess.run([str(tool), '-version'], capture_output=True, text=True,
                                encoding='utf-8', errors='replace')
        text = result.stdout + result.stderr
        version = re.search(r'\b(?:javac|(?:openjdk|java)(?: version)?)\s+"?(\d+)', text)
        if result.returncode or not version or version[1] != '21':
            raise RuntimeError(f'Java 21 required; {tool} reports {text.strip()!r}. '
                               'Set JAVA_HOME to a JDK 21 (or put its javac on PATH).')
    return javac, java


def unique(paths):
    return list(dict.fromkeys(paths))


def runtime_libraries(versions: dict[str, str]) -> list[Path]:
    userdev = cached(f'net.neoforged:neoforge:{versions["neo_version"]}:userdev')
    with zipfile.ZipFile(userdev) as archive:
        libraries = [cached(c) for c in json.loads(archive.read('config.json'))['libraries']]
    classpath = PROJECT / 'build/moddev/clientLegacyClasspath.txt'
    if not classpath.is_file():
        raise RuntimeError(f'Missing existing project runtime classpath: {classpath}; '
                           'prepare project build outputs separately (this runner never invokes Gradle)')
    for line in classpath.read_text(encoding='utf-8').splitlines():
        if line.strip():
            path = Path(line.strip()).expanduser()
            libraries.append(path if path.is_absolute() else PROJECT / path)
    libraries += [PROJECT / f'build/moddev/artifacts/neoforge-{versions["neo_version"]}-merged.jar',
                  cached(f'org.appliedenergistics:appliedenergistics2:{versions["ae2_version"]}')]
    libraries = unique(libraries)
    missing = [str(p) for p in libraries if not p.is_file()]
    if missing:
        raise RuntimeError('Missing existing runtime inputs (prepare build outputs separately):\n' + '\n'.join(missing))
    return libraries


def java_argfile(file: Path, args: list[str]):
    # Java argument files use backslash escaping, including on Windows.
    file.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"'
                              for arg in args) + '\n', encoding='utf-8')


def invoke(executable: Path, args: list[str], directory: Path, logfile: str) -> int:
    directory.mkdir(parents=True, exist_ok=True)
    temporary = directory / 'tmp'
    temporary.mkdir(exist_ok=True)
    argfile = directory / (logfile + '.args')
    java_argfile(argfile, args)
    options = ['-Duser.language=en', '-Duser.country=US', '-Dstdout.encoding=UTF-8',
               '-Dstderr.encoding=UTF-8', f'-Djava.io.tmpdir={temporary}']
    if executable.stem == 'javac':
        options = ['-J' + option for option in options]
    result = subprocess.run([str(executable)] + options + ['@' + str(argfile)], cwd=directory,
                            capture_output=True, text=True, encoding='utf-8', errors='replace')
    text = result.stdout + result.stderr
    (directory / logfile).write_text(text, encoding='utf-8')
    print(text, end='', flush=True)
    return result.returncode


def run(runtime: str) -> int:
    versions = project_versions()
    selected = list(RUNTIMES) if runtime == 'all' else ['old', 'new'] if runtime == 'both' else [runtime]
    # Resolve only requested fixtures; no skipped runtimes, fallback to another version, or downloads.
    jars = {name: runtime_jar(name, versions['minecraft_version']) for name in selected}
    javac, java = java_tools()
    libraries = runtime_libraries(versions)
    main_output = PROJECT / 'build/classes/java/main'
    required = [main_output / f'com/atir/molecularmanipulator/mixin/{name}.class' for name in MIXIN_NAMES]
    required += [main_output / f'com/atir/molecularmanipulator/client/{name}.class'
                 for name in ['RecipeLockTooltips', 'ResponsiveContainerScreen']]
    missing = [str(p) for p in required if not p.is_file()]
    if missing:
        raise RuntimeError('Missing existing compiled production classes:\n' + '\n'.join(missing)
                           + '\nPrepare up-to-date production build outputs separately; this runner only compiles test helpers.')
    helpers = [HELPERS / (name + '.java') for name in ['SmokeMain', 'SmokeMixinService', 'SmokeProperties']]
    missing = [str(p) for p in helpers if not p.is_file()]
    if missing:
        raise RuntimeError('Missing maintained test helpers:\n' + '\n'.join(missing))

    # Fresh output prevents stale success markers/classes from masking a failed regression.
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    helper_classes = WORK / 'harness-classes'
    helper_classes.mkdir()
    cp = os.pathsep.join(map(str, libraries))
    rc = invoke(javac, ['-encoding', 'UTF-8', '--release', '21', '-proc:none', '-implicit:none',
                        '-sourcepath', '', '-classpath', cp, '-d', str(helper_classes)]
                + list(map(str, helpers)), WORK, 'compile-harness.log')
    if rc:
        return rc
    services = helper_classes / 'META-INF/services'
    services.mkdir(parents=True)
    (services / 'org.spongepowered.asm.service.IMixinService').write_text('smoke.SmokeMixinService\n', encoding='utf-8')
    (services / 'org.spongepowered.asm.service.IGlobalPropertyService').write_text('smoke.SmokeProperties\n', encoding='utf-8')
    config = {
        'required': True, 'minVersion': '0.8', 'package': 'com.atir.molecularmanipulator.mixin',
        'compatibilityLevel': 'JAVA_21', 'mixinextras': {'minVersion': '0.5.0'},
        'client': MIXIN_NAMES, 'injectors': {'defaultRequire': 1},
    }
    (helper_classes / 'jei-recipe-lock-smoke.mixins.json').write_text(json.dumps(config, indent=2), encoding='utf-8')

    exitcode = 0
    for name, jar in jars.items():
        directory = WORK / name
        directory.mkdir()
        nested = []
        # Modern JEI bundles mezz_config; link against the real bundled classes too.
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if entry.startswith('META-INF/jarjar/') and entry.endswith('.jar'):
                    destination = directory / 'nested' / Path(entry).name
                    destination.parent.mkdir(exist_ok=True)
                    destination.write_bytes(archive.read(entry))
                    nested.append(destination)
        runtime_cp = unique([helper_classes, jar, main_output] + nested + libraries)
        cp = os.pathsep.join(map(str, runtime_cp))
        print(f'\n=== Real Mixin / Java 21 smoke: {name}: {jar.name} ===', flush=True)
        rc = invoke(java, ['-Xverify:all', '-Dmixin.debug.verbose=true', '-Dmixin.debug.export=true',
                           '-Dmixin.debug.export.decompile=false', '-classpath', cp,
                           'smoke.SmokeMain', str(directory), str(jar)], directory, 'transform.log')
        passed = rc == 0 and (directory / 'success.txt').is_file()
        print(f'{name}: {"PASS" if passed else "FAIL"}; output: {directory}', flush=True)
        if not passed:
            exitcode = rc or 1
    return exitcode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', choices=['old', 'new', 'latest', 'both', 'all'], default='old',
                        help='JEI runtime(s) to require and test (default: old)')
    args = parser.parse_args()
    try:
        return run(args.runtime)
    except (RuntimeError, OSError, zipfile.BadZipFile) as error:
        print(f'JEI/Mixin smoke failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    sys.exit(main())
