from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]


class PackagedAgentLauncherTests(unittest.TestCase):
    def test_executable_jar_loads_the_same_agent_before_unmodified_paper(self):
        with zipfile.ZipFile(ROOT / 'redstone-agent/target/MandoMC-Launcher.jar') as launcher, \
                zipfile.ZipFile(ROOT / 'redstone-agent/target/MandoMC-RedstoneAgent.jar') as agent:
            manifest = launcher.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '').replace('\r\n', '\n')
            self.assertIn('Main-Class: io.papermc.paperclip.Main\n', manifest)
            self.assertIn('Class-Path: paper.jar\n', manifest)
            self.assertIn('Launcher-Agent-Class: net.mandomc.redstone.RedstoneAgent\n', manifest)
            classes = [name for name in launcher.namelist() if name.endswith('.class')]
            self.assertTrue(classes)
            for name in classes:
                self.assertTrue(name.startswith('net/mandomc/redstone/'))
                self.assertEqual(launcher.read(name), agent.read(name))
            self.assertFalse(any(name.endswith('.jar') or name.startswith('io/papermc/') for name in launcher.namelist()))
