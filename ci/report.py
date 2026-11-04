import glob, os, re, sys

def esc(s):
    return s.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')

def emit(title, text, limit=3500):
    text = text.strip()
    if text:
        print(f"::error title={title}::{esc(text[:limit])}")

code = open('/tmp/ci/exit_code').read().strip() if os.path.exists('/tmp/ci/exit_code') else 'missing'
print('gradle exit code:', code)
for f in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    s = open(f, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure message="([^"]*)"[^>]*>(.*?)</failure>', s, re.S):
        emit('FAILED ' + m.group(1), m.group(3))
for name in ('crash.log', 'runtime.log'):
    p = '/tmp/ci/' + name
    if os.path.exists(p):
        emit(name, open(p, errors='replace').read())
if os.path.exists('/tmp/ci/gradle.log'):
    tail = open('/tmp/ci/gradle.log', errors='replace').read()[-2500:]
    emit('gradle tail', tail)
sys.exit(0 if code == '0' else 1)
