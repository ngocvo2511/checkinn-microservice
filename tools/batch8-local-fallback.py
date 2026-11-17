"""Run existing native acceptance on NEW local DBs; preserve all historical evidence."""
import importlib.util
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('native_batch8',ROOT/'tools/batch7b-schema-verify.py')
native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
native.OUT=ROOT/'Docs/evidence/batch-8-hosted-e2e-performance/local-disposable'
native.main()
