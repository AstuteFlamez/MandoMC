import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("server_control", Path(__file__).parents[1] / "server-control.py")
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)


class ServerControlTests(unittest.TestCase):
    def test_same_jar_name_in_another_project_is_not_ours(self):
        def output(*args):
            if args[0] == "ps":
                return "/jdk/bin/java -Xmx4G -jar paper.jar --nogui"
            return "p123\nn/another/server/runtime"
        with patch.object(control, "output", side_effect=output):
            self.assertFalse(control.project_process(123))

    def test_unrelated_command_in_runtime_is_not_ours(self):
        def output(*args):
            return "python3 worker.py" if args[0] == "ps" else f"p123\nn{control.RUNTIME}"
        with patch.object(control, "output", side_effect=output):
            self.assertFalse(control.project_process(123))

    def test_reused_pid_rejects_stale_state(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "state.json"
            state.write_text(json.dumps({"pid": 123, "identity": "old start", "log-offset": 0}))
            with patch.object(control, "STATE", state), patch.object(control, "identity", return_value="new start"), patch.object(control, "project_process", return_value=True):
                self.assertIsNone(control.saved())

    def test_shutdown_timeout_does_not_force_kill(self):
        record = {"pid": 123, "identity": "same start"}
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(control, "LOCAL", Path(directory)), patch.object(control, "find_server", return_value=(123, record)), patch.object(control, "identity", return_value="same start"), patch.object(control, "project_process", return_value=True), patch.object(control, "send") as send, patch.object(control.os, "kill") as kill, patch.object(control.time, "monotonic", side_effect=[0, 46]):
                with self.assertRaisesRegex(RuntimeError, "No forced kill"):
                    control.stop()
                send.assert_called_once_with(record, "stop")
                kill.assert_not_called()

    def test_console_rejects_multiple_commands_in_one_line(self):
        with self.assertRaisesRegex(RuntimeError, "one nonempty line"):
            control.send({}, "list\nstop")


if __name__ == "__main__":
    unittest.main()
