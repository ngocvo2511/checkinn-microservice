import importlib.util
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('native',R/'tools/batch7b-schema-verify.py');n=importlib.util.module_from_spec(s);s.loader.exec_module(n)
n.OUT=R/'Docs/evidence/batch-8c-runtime-stability/local-disposable'
if __name__=='__main__':n.main()
