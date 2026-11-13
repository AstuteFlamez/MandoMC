import errno
import importlib.util
import io
import json
from pathlib import Path
import stat
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('pebblehost', Path(__file__).parents[1] / 'pebblehost.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class Remote:
    def __init__(self, files=None):
        self.files = dict(files or {})
        self.directories = {'.', '.mandomc', 'plugins', 'plugins/update'}
        self.renames = []

    def lstat(self, path):
        if path in self.files:
            return type('Attributes', (), {'st_mode': stat.S_IFREG})()
        if path in self.directories:
            return type('Attributes', (), {'st_mode': stat.S_IFDIR})()
        raise FileNotFoundError(errno.ENOENT, 'not found')

    def mkdir(self, path):
        if path in self.directories:
            raise FileExistsError(errno.EEXIST, 'locked')
        self.directories.add(path)

    def rmdir(self, path):
        self.directories.remove(path)

    def open(self, path, mode):
        if mode == 'rb':
            return io.BytesIO(self.files[path])
        remote = self

        class Writer(io.BytesIO):
            def close(self):
                remote.files[path] = self.getvalue()
                super().close()

        return Writer()

    def posix_rename(self, source, target):
        self.files[target] = self.files.pop(source)
        self.renames.append((source, target))

    def rename(self, source, target):
        if target in self.files:
            raise OSError(errno.EEXIST, 'exists')
        self.posix_rename(source, target)


class SftpSafetyTests(unittest.TestCase):
    def test_rejects_path_traversal_and_absolute_paths(self):
        for path in ('/etc/passwd', '../world', 'plugins/../ops.json', './plugins', 'a\\b', '', 'a//b'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                module.safe_path(path)

    def test_runtime_data_and_unmanaged_paths_cannot_be_payload_targets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'deployment').mkdir()
            (root / 'source.yml').write_text('config')
            for target in ('ops.json', 'dev_world/level.dat', 'plugins/MandoMC/data/state.yml',
                           'plugins/Votifier/config.yml', 'plugins/Votifier/rsa/private.key',
                           'plugins/VotingPlugin/Config.yml'):
                (root / 'deployment/files.json').write_text(json.dumps({'files': [
                    {'source': 'source.yml', 'target': target}]}))
                with self.assertRaises(ValueError):
                    module.payload(root)

    def test_remote_drift_stops_entire_plan_before_writes(self):
        remote = Remote({'plugins/update/MandoMC.jar': b'edited by someone'})
        with self.assertRaisesRegex(ValueError, 'Production drift'):
            module.plan(remote, {'plugins/update/MandoMC.jar': b'new'},
                        {'files': {'plugins/update/MandoMC.jar': module.sha(b'previous')}})
        self.assertEqual(remote.renames, [])

    def test_new_config_is_allowed_but_existing_unmanaged_config_is_not(self):
        target = 'plugins/MandoMC/new.yml'
        self.assertEqual(module.plan(Remote(), {target: b'x'}, {'files': {}}), [(target, b'x', None)])
        with self.assertRaisesRegex(ValueError, 'Production drift'):
            module.plan(Remote({target: b'hand edited'}), {target: b'x'}, {'files': {}})

    def test_removed_managed_file_requires_reviewed_migration(self):
        with self.assertRaisesRegex(ValueError, 'removal'):
            module.plan(Remote(), {}, {'files': {'plugins/MandoMC/old.yml': 'hash'}})

    def test_consumed_update_uses_installed_plugin_for_drift_check(self):
        remote = Remote({'plugins/MandoMC.jar': b'old'})
        changes = module.plan(remote, {'plugins/update/MandoMC.jar': b'new'},
                              {'files': {'plugins/update/MandoMC.jar': module.sha(b'old')}})
        self.assertEqual(changes, [('plugins/update/MandoMC.jar', b'new', b'old')])

    def test_idempotent_upload_skips_already_uploaded_bytes(self):
        remote = Remote({'plugins/update/MandoMC.jar': b'new'})
        self.assertEqual(module.plan(remote, {'plugins/update/MandoMC.jar': b'new'}, {'files': {}}), [])

    def test_atomic_upload_is_verified_before_rename(self):
        remote = Remote()
        module.atomic_write(remote, '.mandomc/state.json', b'new')
        self.assertEqual(remote.files['.mandomc/state.json'], b'new')
        self.assertEqual(len(remote.renames), 1)

    def test_missing_bootstrap_receipt_prevents_upload(self):
        with self.assertRaisesRegex(ValueError, 'migration'):
            module.deploy(Remote(), 'a' * 40, apply=True)

    def test_replacement_on_pebblehost_keeps_old_bytes_before_installing_new(self):
        class PebbleRemote(Remote):
            def posix_rename(self, source, target):
                raise OSError(errno.ENOTSUP, 'extension unsupported')

            def rename(self, source, target):
                if target in self.files:
                    raise OSError(errno.EEXIST, 'exists')
                self.files[target] = self.files.pop(source)
                self.renames.append((source, target))

        remote = PebbleRemote({'start.sh': b'old'})
        module.atomic_write(remote, 'start.sh', b'new')
        self.assertEqual(remote.files['start.sh'], b'new')
        previous = [value for name, value in remote.files.items() if '.mandomc-previous-' in name]
        self.assertEqual(previous, [b'old'])

    def test_new_immutable_agent_keeps_running_agent_untouched(self):
        old = 'agents/MandoMC-RedstoneAgent-' + module.sha(b'old') + '.jar'
        new = 'agents/MandoMC-RedstoneAgent-' + module.sha(b'new') + '.jar'
        remote = Remote({old: b'old'})
        self.assertEqual(module.plan(remote, {new: b'new'}, {'files': {old: module.sha(b'old')}}),
                         [(new, b'new', None)])
        self.assertEqual(remote.files[old], b'old')

    def test_host_startup_selects_java25_and_exact_immutable_agent(self):
        target = 'agents/MandoMC-RedstoneAgent-' + 'a' * 64 + '.jar'
        old = b'javaversion=21\nstartupflags=--enable-preview -javaagent:agents/MandoMC-RedstoneAgent.jar\nautoupdate=true\nheaplimited=1\n'
        updated = module.host_settings(old, {target: b'agent'})
        self.assertIn(b'javaversion=25\n', updated)
        self.assertIn(b'autoupdate=false\n', updated)
        self.assertIn(b'heaplimited=1\n', updated)
        self.assertIn(('startupflags=--enable-preview -javaagent:' + target).encode(), updated)

    def test_backup_precedes_overwrite_and_no_restart_is_sent(self):
        from unittest.mock import patch
        target = 'plugins/update/MandoMC.jar'
        old_state = json.dumps({'files': {target: module.sha(b'old')}}).encode()
        remote = Remote({'.mandomc/bootstrap.json': b'{"schemaVersion":1,"verifiedStartup":true}',
                         '.mandomc/deployed.json': old_state, 'plugins/MandoMC.jar': b'old',
                         'server.properties': b'online-mode=true\n', 'pebblehost.yml': b'javaversion=25\n'})
        with patch.object(module, 'payload', return_value={target: b'new'}), \
                patch.object(module, 'production_properties', side_effect=lambda data, commit: data), \
                patch.object(module, 'host_settings', side_effect=lambda data, files: data):
            module.deploy(remote, 'a' * 40, apply=True)
        destinations = [target for _, target in remote.renames]
        self.assertIn('/files/server.properties', destinations[1])
        self.assertIn('/files/pebblehost.yml', destinations[2])
        self.assertIn('/files/plugins/update/MandoMC.jar', destinations[3])
        self.assertEqual(destinations[4], target)
        self.assertEqual(destinations[5], '.mandomc/deployed.json')
        state = json.loads(remote.files['.mandomc/deployed.json'])
        self.assertTrue(state['restartRequired'])
        self.assertNotIn('.mandomc/deploy.lock', remote.directories)

    def test_pack_and_motd_update_preserves_host_settings_and_secrets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'resource-pack/build').mkdir(parents=True)
            (root / 'server/runtime').mkdir(parents=True)
            (root / 'resource-pack/build/MandoMC.json').write_text(json.dumps(
                dict(minecraftVersion='26.1.2', sha1='b' * 40)))
            (root / 'resource-pack/hosting.json').write_text(json.dumps(
                dict(repository='AstuteFlamez/MandoMC', packId='stable-id', required=True, prompt='Equipment')))
            (root / 'server/runtime/server.properties.template').write_text('motd=§cAlpha\\nJoin Discord!\n')
            original = b'server-port=25570\nrcon.password=private-value\nonline-mode=true\nmotd=old\n'
            updated = module.production_properties(original, 'a' * 40, root)
            self.assertIn(b'server-port=25570\nrcon.password=private-value\nonline-mode=true\n', updated)
            self.assertIn(b'motd=\\u00a7cAlpha\\nJoin Discord!\n', updated)
            self.assertIn(b'resource-pack-26.1.2-aaaaaaaaaaaa/MandoMC.zip', updated)
            self.assertIn(b'resource-pack-sha1=' + b'b' * 40, updated)


if __name__ == '__main__':
    unittest.main()
