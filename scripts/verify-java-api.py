#!/usr/bin/env python3
"""Fail a scoped Java overlay if it drops any original public method/field signature."""
import argparse,json,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',required=True);p.add_argument('--candidate',required=True)
p.add_argument('--class',dest='classes',action='append',required=True)
p.add_argument('--javap');a=p.parse_args()
java_home=os.environ.get('MANDOMC_JAVA_HOME') or os.environ.get('JAVA_HOME')
javap=a.javap or (str(Path(java_home)/'bin/javap') if java_home else 'javap')
def api(path,name):
 result=subprocess.run([javap,'-classpath',path,name],capture_output=True,text=True,check=True)
 return {line.strip() for line in result.stdout.splitlines() if line.strip().startswith('public ') and line.strip().endswith(';')}
for name in a.classes:
 before=api(a.baseline,name);after=api(a.candidate,name)
 missing=before-after
 if missing:raise SystemExit('Dropped public API in '+name+': '+json.dumps(sorted(missing)))
 print(name+': original public API retained')
