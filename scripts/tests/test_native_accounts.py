import importlib.util
import tempfile
from pathlib import Path
import unittest
from uuid import uuid4

spec = importlib.util.spec_from_file_location('accounts', Path(__file__).resolve().parents[1] / 'import-native-accounts.py')
accounts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(accounts)


class MigrationTests(unittest.TestCase):
    def test_memberships_preserved_without_editor_metadata(self):
        uid = str(uuid4())
        row = dict(UUID=uid, PERMISSION='group.builder', VALUE='true', SERVER='global', WORLD='global', CONTEXTS='{}', EXPIRY='0')
        users = accounts.memberships([dict(UUID=uid, PRIMARY_GROUP='default')], [row, dict(row, PERMISSION='meta.private-key')])
        self.assertEqual(users[uid]['ranks'], ['builder', 'default'])
        with self.assertRaises(ValueError):
            accounts.memberships([], [dict(row, WORLD='ilum')])

    def test_balance_migration_newer_snapshot_wins(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old, new = root/'old', root/'new'
            old.mkdir(); new.mkdir()
            uid = str(uuid4())
            (old/f'{uid}.yml').write_text("money: '17.50'\n")
            (new/f'{uid}.yml').write_text("money: '21.25'\n")
            self.assertEqual(accounts.balances([old, new]), {uid: 2125})
            (new/f'{uid}.yml').write_text("money: '-1'\n")
            with self.assertRaises(ValueError):
                accounts.balances([old, new])
