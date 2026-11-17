"""Batch8B canonical acceptance: new disposable local PostgreSQL, never hosted."""
import importlib.util
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('native',ROOT/'tools/batch7b-schema-verify.py')
native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
native.OUT=ROOT/'Docs/evidence/batch-8b-hosted-e2e-performance/local-disposable'
if __name__=='__main__':native.main()
