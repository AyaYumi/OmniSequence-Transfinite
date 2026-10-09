"""Prepare real, compile-only optional CPU contracts without changing runtime mods."""
from pathlib import Path
import hashlib
import os
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LIBS = ROOT / "libs"
ARTIFACTS = (
    ("neoecoae-21.2.0.jar", "NEOECO_API_JAR_URL",
     "https://cdn.modrinth.com/data/udZtKfzP/versions/cyNfBT7v/neoecoae-21.2.0.jar",
     "87f52e9aa99cf7f9ae25925e46b038fc7df9e9df1f764cf4daa78a9be8bac084",
     "cn/dancingsnow/neoecoae/api/me/provider/ECOFastPathDispatchProvider.class"),
    ("thunderbolt-batch-api.jar", "THUNDERBOLT_API_JAR_URL",
     "https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/releases/download/2.0.3-beta/thunderbolt-2.0.3-beta.jar",
     "23c01714582514b37329d674539e0805095aefd9fc56a9bfa84de2f45c331890",
     "com/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider.class"),
    ("data_energistics-3.3.0-api.jar", "DATA_ENERGISTICS_API_JAR_URL",
     "https://github.com/ModularMCLib/DataEnergistics/releases/download/v3.3.0-1.21/data_energistics-1.21.1-3.3.0.jar",
     "ec5e729f0562dc939301d8ddf52374604f50456b3e526b9332286f849e01a6e1",
     "com/fish_dan_/data_energistics/api/crafting/dispatch/CountedCraftingProviderAdapter.class"),
)


def validate(path, required_class):
    with zipfile.ZipFile(path) as jar:
        if required_class not in jar.namelist():
            raise RuntimeError(f"{path.name} does not provide {required_class}")


def main():
    LIBS.mkdir(exist_ok=True)
    for filename, variable, default_url, expected_hash, required_class in ARTIFACTS:
        target = LIBS / filename
        if target.is_file():
            validate(target, required_class)
            print(f"Using local optional API: {filename}")
            continue
        url = os.environ.get(variable) or default_url
        temporary = target.with_suffix(".download")
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "OmniSequence-build"})
            with urllib.request.urlopen(request, timeout=60) as response:
                temporary.write_bytes(response.read())
            if url == default_url and hashlib.sha256(temporary.read_bytes()).hexdigest() != expected_hash:
                raise RuntimeError(f"Unexpected SHA256 for {filename}")
            validate(temporary, required_class)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        print(f"Prepared compile-only API: {filename}")


if __name__ == "__main__":
    main()
