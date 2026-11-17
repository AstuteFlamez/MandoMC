import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('guide_manifest', ROOT / 'scripts/build-guide-knowledge.py')
guide = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guide)


class GuideKnowledgeManifestTest(unittest.TestCase):
    def test_public_allowlist_excludes_private_and_agent_sources(self):
        for path in ['plugin/src/main/resources/items/recipes.yml',
                     'plugin/src/main/java/net/mandomc/gameplay/vehicles/VehicleRepairs.java',
                     'docs/features/vehicles-and-explosions.md']:
            self.assertTrue(guide.approved(path), path)
        for path in ['.local/secrets/groq-api-key', 'AGENTS.md', 'docs/status.md',
                     'server/runtime/playerdata/example.dat', 'plugins/MandoMC/data/vehicles.yml',
                     'plugin/src/main/java/net/mandomc/gameplay/guide/GroqBrain.java',
                     'plugin/src/main/java/net/mandomc/gameplay/admin/StaffInventory.java']:
            self.assertFalse(guide.approved(path), path)

    def test_current_manifest_pins_tracked_public_content_with_hashes(self):
        result = guide.manifest()
        self.assertRegex(result['commit'], r'^[a-f0-9]{40}$')
        self.assertGreater(len(result['sources']), 100)
        for source in result['sources']:
            self.assertTrue(guide.approved(source['path']))
            self.assertRegex(source['sha256'], r'^[a-f0-9]{64}$')
            self.assertEqual((ROOT / source['path']).stat().st_size, source['bytes'])
