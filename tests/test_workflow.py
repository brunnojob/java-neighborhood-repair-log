import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        home = os.environ.get("JAVA_HOME")
        self.java = str(Path(home) / "bin/java") if home else "java"
        compiler = str(Path(home) / "bin/javac") if home else "javac"
        subprocess.run(
            [
                compiler,
                "-Xlint:all",
                "-Werror",
                "-d",
                str(self.directory),
                str(Path(__file__).resolve().parents[1] / "RepairLog.java"),
            ],
            check=True,
        )

    def raw(self, *arguments):
        return subprocess.run(
            [self.java, "-cp", str(self.directory), "RepairLog", *map(str, arguments)],
            capture_output=True,
            text=True,
        )

    def command(self, *arguments):
        result = self.raw(*arguments)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout) if result.stdout.strip() else None

    def test_persistent_workflow(self):
        file = self.directory / "repairs.log"
        ticket = self.command(file, "add", "zone", "3", "repair")["tickets"][0]
        self.command(file, "assign", ticket["id"], "1", "operator")
        self.assertNotEqual(
            self.raw(
                file, "transition", ticket["id"], "1", "IN_PROGRESS", "start"
            ).returncode,
            0,
        )
        result = self.command(
            file, "transition", ticket["id"], "2", "IN_PROGRESS", "start"
        )
        self.assertEqual(result["tickets"][0]["revision"], 3)
        file.write_text(file.read_text()[:-3] + "ab\n")
        self.assertNotEqual(self.raw(file, "report").returncode, 0)
