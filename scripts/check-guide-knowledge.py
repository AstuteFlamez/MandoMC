#!/usr/bin/env python3
"""Reject a server artifact with missing, stale or incomplete M4-ND knowledge before upload."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import runpy
import zipfile

ROOT = Path(__file__).resolve().parents[1]
builder = runpy.run_path(str(ROOT / 'scripts/build-guide-knowledge.py'))


def check(root=ROOT, release=False, jar=None):
    expected = builder['manifest'](root)
    artifact = jar or root / 'plugin/target/MandoMC.jar'
    with zipfile.ZipFile(artifact) as archive:
        for name in ('guide-repository.json', 'guide-public.json.gz'):
            if archive.getinfo(name).file_size > 8 * 1024 * 1024:
                raise ValueError('M4-ND knowledge resource exceeds its bound')
        raw = archive.read('guide-repository.json')
        manifest = json.loads(raw)
        with gzip.GzipFile(fileobj=io.BytesIO(archive.read('guide-public.json.gz'))) as stream:
            body = stream.read(32 * 1024 * 1024 + 1)
    if len(body) > 32 * 1024 * 1024:
        raise ValueError('M4-ND knowledge bundle exceeds its expanded bound')
    bundle = json.loads(body)
    if manifest['commit'] != expected['commit'] or manifest['sources'] != expected['sources']:
        raise ValueError('M4-ND knowledge is stale: rebuild from the exact release source')
    if release and (not manifest['clean'] or not expected['clean']):
        raise ValueError('Production M4-ND knowledge requires a clean, committed release build')
    if bundle['commit'] != manifest['commit'] or bundle['manifestHash'] != hashlib.sha256(raw).hexdigest():
        raise ValueError('M4-ND bundled knowledge belongs to a different build')
    if set(bundle['documents']) != {s['path'] for s in manifest['sources']}:
        raise ValueError('M4-ND bundled knowledge source coverage is incomplete')
    for source in manifest['sources']:
        data = bundle['documents'][source['path']].encode('utf-8')
        if len(data) != source['bytes'] or hashlib.sha256(data).hexdigest() != source['sha256']:
            raise ValueError('M4-ND bundled source differs: ' + source['path'])
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', action='store_true')
    args = parser.parse_args()
    result = check(release=args.release)
    print(f"M4-ND knowledge verified: {len(result['sources'])} exact-build sources @ {result['commit'][:12]}")
