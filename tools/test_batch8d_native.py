"""The native acceptance command must fail for skipped or failed Maven runs."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('native_runner', Path(__file__).with_name('batch8d-native.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class NativeAcceptanceTest(unittest.TestCase):
    def test_accepts_executed_success(self):
        native.require_test_passed(dict(exitCode=0, summary=[1, 0, 0, 0], buildSuccess=True))

    def test_rejects_failed_skipped_missing_or_empty_results(self):
        valid = dict(exitCode=0, summary=[1, 0, 0, 0], buildSuccess=True)
        for changes in [dict(exitCode=1), dict(summary=[1, 0, 0, 1]), dict(summary=None),
                        dict(summary=[0, 0, 0, 0]), dict(summary=[1, 1, 0, 0]), dict(buildSuccess=False)]:
            with self.subTest(changes=changes), self.assertRaises(RuntimeError):
                native.require_test_passed({**valid, **changes})


if __name__ == '__main__':
    unittest.main()
