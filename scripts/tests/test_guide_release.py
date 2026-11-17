import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('guide_release', ROOT / 'scripts/check-guide-knowledge.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class GuideReleaseTest(unittest.TestCase):
    def fixture(self, root, tamper=False, incomplete=False):
        source = root / 'plugin/src/main/resources/items/recipes.yml'
        source.parent.mkdir(parents=True)
        source.write_text('schema-version: 1\nsources:\n  coal: Tatooine caves\n')
        for command in [['git', 'init', '-q'], ['git', 'add', '.'],
                        ['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'fixture']]:
            subprocess.run(command, cwd=root, check=True, capture_output=True)
        manifest = release.builder['manifest'](root)
        raw = json.dumps(manifest).encode()
        documents = {s['path']: (root / s['path']).read_text() for s in manifest['sources']}
        if tamper:
            documents[next(iter(documents))] += 'tampered'
        if incomplete:
            documents.clear()
        bundle = {'commit': manifest['commit'], 'manifestHash': hashlib.sha256(raw).hexdigest(), 'documents': documents}
        jar = root / '.build/MandoMC.jar'
        jar.parent.mkdir()
        (root / '.git/info/exclude').write_text('.build/\n')
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('guide-repository.json', raw)
            archive.writestr('guide-public.json.gz', gzip.compress(json.dumps(bundle).encode()))
        return jar, source

    def test_clean_exact_release_passes_and_stale_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jar, source = self.fixture(root)
            release.check(root, release=True, jar=jar)
            source.write_text(source.read_text().replace('Tatooine', 'Naboo'))
            with self.assertRaisesRegex(ValueError, 'stale'):
                release.check(root, release=True, jar=jar)

    def test_tampered_or_incomplete_bundle_is_rejected(self):
        for flags in [{'tamper': True}, {'incomplete': True}]:
            with self.subTest(flags=flags), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                jar, _ = self.fixture(root, **flags)
                with self.assertRaises(ValueError):
                    release.check(root, release=True, jar=jar)

    def test_uncommitted_release_is_rejected_even_when_public_sources_match(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jar, _ = self.fixture(root)
            (root / 'unreviewed.txt').write_text('pending')
            with self.assertRaisesRegex(ValueError, 'clean'):
                release.check(root, release=True, jar=jar)
