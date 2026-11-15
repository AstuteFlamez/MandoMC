import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'sync-resource-pack.py'
spec = importlib.util.spec_from_file_location('pack_sync', SCRIPT)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class PackSyncTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'resource-pack').mkdir()
        (self.root / 'server').mkdir()
        self.url = 'https://github.com/AstuteFlamez/MandoMC/releases/download/resource-pack-26.1.2-123456789abc/MandoMC.zip'
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('pack.mcmeta', json.dumps({'pack': {'min_format': [84, 0], 'max_format': [84, 0]}}))
        self.data = buffer.getvalue()
        self.manifest = {
            'schemaVersion': 1, 'minecraftVersion': '26.1.2', 'resourceFormat': [84, 0],
            'sha1': hashlib.sha1(self.data).hexdigest(), 'sha256': hashlib.sha256(self.data).hexdigest(),
            'bytes': len(self.data), 'url': self.url,
        }
        config = {'repository': 'AstuteFlamez/MandoMC', 'packId': '0e5e35ef-d5bb-4ce8-af01-e62dcefb6202',
                  'required': True, 'prompt': 'MandoMC pack'}
        (self.root / 'resource-pack/hosting.json').write_text(json.dumps(config))
        (self.root / 'resource-pack/sources.json').write_text(json.dumps(self.manifest))
        (self.root / 'server/paper.lock.json').write_text(json.dumps({'minecraftVersion': '26.1.2'}))
        self.properties = self.root / 'server/server.properties'
        self.original = '# Keep this comment\nserver-port=25565\nlevel-name=players_world\nresource-pack=old\nresource-pack=duplicate\n'
        self.properties.write_text(self.original)

    def run_sync(self, offline=False):
        arguments = [str(SCRIPT), '--properties', str(self.properties)] + (['--offline'] if offline else [])
        with patch.object(sync, 'ROOT', self.root), patch.object(sync, 'assert_stopped'), patch.object(sys, 'argv', arguments):
            sync.main()

    def cache(self):
        directory = self.properties.parent / 'resource-packs'
        directory.mkdir()
        (directory / 'current.json').write_text(json.dumps(self.manifest))
        (directory / (self.manifest['sha256'] + '.zip')).write_bytes(self.data)

    def test_selects_matching_minecraft_version_and_ignores_drafts(self):
        def release(version, date, draft=False):
            return {'tag_name': f'resource-pack-{version}-123456789abc', 'published_at': date, 'draft': draft}
        correct = release('26.1.2', '2026-11-05T01:00:00Z')
        candidates = [correct, release('26.3', '2026-11-06T01:00:00Z'), release('26.1.2', '2026-11-07T01:00:00Z', True)]
        self.assertEqual(sync.select_release(candidates, '26.1.2'), correct)

    def test_download_updates_only_pack_settings(self):
        with patch.object(sync, 'latest_pack', return_value=(self.manifest, self.data)):
            self.run_sync()
        text = self.properties.read_text()
        self.assertIn('# Keep this comment\nserver-port=25565\nlevel-name=players_world\n', text)
        self.assertEqual(text.count('\nresource-pack='), 1)
        self.assertIn('resource-pack=' + self.url, text)
        self.assertIn('require-resource-pack=true', text)
        self.assertIn('resource-pack-sha1=' + self.manifest['sha1'], text)

    def test_corrupt_download_does_not_change_server_settings(self):
        with patch.object(sync, 'latest_pack', return_value=(self.manifest, self.data + b'changed')):
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                self.run_sync()
        self.assertEqual(self.properties.read_text(), self.original)

    def test_network_failure_uses_verified_cache(self):
        self.cache()
        with patch.object(sync, 'latest_pack', side_effect=OSError('network unavailable')):
            self.run_sync()
        self.assertIn('resource-pack=' + self.url, self.properties.read_text())

    def test_cache_for_another_version_does_not_change_settings(self):
        self.manifest['minecraftVersion'] = '26.3'
        self.cache()
        with self.assertRaisesRegex(ValueError, 'wrong schema or Minecraft version'):
            self.run_sync(offline=True)
        self.assertEqual(self.properties.read_text(), self.original)

    def test_running_server_is_rejected(self):
        with patch.object(sync.socket, 'create_connection'):
            with self.assertRaisesRegex(ValueError, 'Stop the server'):
                sync.assert_stopped(self.original)

    def test_explicit_pin_survives_a_newer_main_release(self):
        self.cache()
        pin = self.properties.parent / 'resource-packs/pinned.json'
        pin.write_text(json.dumps(self.manifest))
        with patch.object(sync, 'latest_pack', side_effect=AssertionError('Must not replace the explicit selection')):
            self.run_sync()
        self.assertIn('resource-pack=' + self.url, self.properties.read_text())

    def test_invalid_pin_fails_closed_instead_of_replacing_it_with_cached_main(self):
        self.cache()
        pin = self.properties.parent / 'resource-packs/pinned.json'
        invalid = dict(self.manifest, sha1='0' * 40)
        pin.write_text(json.dumps(invalid))
        with patch.object(sync, 'latest_pack', side_effect=AssertionError('Must not discard an invalid explicit selection')):
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                self.run_sync()
        self.assertEqual(self.properties.read_text(), self.original)


if __name__ == '__main__':
    unittest.main()
