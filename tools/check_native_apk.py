#!/usr/bin/env python3
"""Reject Android 5.x crypto collisions in the actual APK, for every packaged ABI."""
import argparse
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


def check(apk, readelf):
    errors = []
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as temp:
        libraries = [n for n in archive.namelist() if n.startswith("lib/") and n.endswith(".so")]
        abis = {n.split("/")[1] for n in libraries}
        if not {"armeabi-v7a", "arm64-v8a"}.issubset(abis):
            errors.append("Both ARM32 and ARM64 libraries must be packaged")
        if any(Path(n).name in {"libcrypto.so", "libssl.so"} for n in libraries):
            errors.append("APK still packages system-colliding OpenSSL shared libraries")
        for abi in sorted(abis):
            name = f"lib/{abi}/libairplay_native.so"
            if name not in libraries:
                errors.append(f"{abi}: no AirPlay native library")
                continue
            path = Path(temp) / f"{abi}.so"
            path.write_bytes(archive.read(name))
            dynamic = subprocess.check_output([readelf, "--dynamic", str(path)], text=True)
            if re.search(r"NEEDED.*lib(?:crypto|ssl)\.so", dynamic):
                errors.append(f"{abi}: native core still dynamically depends on OpenSSL")
            symbols = subprocess.check_output([readelf, "--wide", "--dyn-syms", str(path)], text=True)
            crypto = re.compile(r"\b(?:EVP_|OPENSSL_|CRYPTO_|AES_|RSA_|BN_|X509_|BIO_|ERR_|HMAC_|SHA[0-9_])")
            exposed = [line.strip() for line in symbols.splitlines() if crypto.search(line)]
            if exposed:
                errors.append(f"{abi}: crypto symbols are imported or exported: {exposed[:3]}")
            print(f"Checked {abi}: dependency table and dynamic symbol table")
    if errors:
        raise SystemExit("\n".join(errors))
    print("PASS: all ABIs embed isolated crypto; no libcrypto.so/libssl.so dependency")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("--readelf", default="readelf")
    args = parser.parse_args()
    check(args.apk, args.readelf)
