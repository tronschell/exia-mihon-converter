#!/usr/bin/env python3
"""Translates Keiyoushi's extensions to JavaScript and publishes them to Project Exia's mirror (mihon.projectexia.com).

Only new or updated extensions are translated (or all of them when the engine changed); the index goes up last.
Usage: python3 tools/publish.py [--langs es,en] [--jobs 6] [--no-upload] [--limit N]
Uploads go to the mirror with EXIA_MIHON_UPLOAD_TOKEN (the nightly GitHub Actions run has it as a secret); without
it, through npx wrangler (logged in to the account). What was published is kept in the mirror's state.json, so a run
on a fresh machine picks up where the last one stopped. Needs JDK 21.
"""
import argparse, concurrent.futures, gzip, hashlib, json, os, subprocess, sys, time, urllib.error, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
exec(open(os.path.join(HERE, 'pbcore.py')).read())
INDEX = 'https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb'
REPO = 'keiyoushi'
BUCKET = 'exia-mihon'
WORK = os.path.join(ROOT, 'build', 'publish')
STATE = os.path.join(WORK, 'state.json')
MIRROR = os.environ.get('EXIA_MIHON_MIRROR', 'https://mihon.projectexia.com')
TOKEN = os.environ.get('EXIA_MIHON_UPLOAD_TOKEN')
BATCH = 60


def signed(v):
    return v - (1 << 64) if v >= (1 << 63) else v


def read_index():
    top = parse(gzip.decompress(urllib.request.urlopen(INDEX, timeout=60).read()))
    meta = {f: v for f, v in top if f != 101}
    store = [v for f, v in top if f == 101][0]
    rows = []
    for raw in (v for f, v in parse(store) if f == 1):
        e = parse(raw)
        g = lambda n: [v for f, v in e if f == n]
        res = dict(parse(g(3)[0])) if g(3) else {}
        sources = []
        for s in g(8):
            s = dict(parse(s))
            sources.append({'id': str(signed(s.get(1, 0))), 'name': s.get(2, b'').decode(), 'lang': s.get(3, b'').decode(),
                            'baseUrl': s.get(4, b'').decode()})
        rows.append({'pkg': g(2)[0].decode(), 'name': g(1)[0].decode(), 'lib': (g(4) or [b''])[0].decode(),
                     'versionCode': (g(5) or [0])[0], 'versionName': (g(6) or [b''])[0].decode(), 'nsfw': (g(7) or [0])[0],
                     'icon': res.get(2, b'').decode(), 'jar': res.get(501, b'').decode(), 'sources': sources})
    return {'name': meta.get(1, b'').decode(), 'shortName': meta.get(2, b'').decode(),
            'fingerprint': meta.get(3, b'').decode()}, rows


def engine_version():
    """Changes whenever the runtime or translator does, so every extension is translated again."""
    out = subprocess.run(['git', 'log', '-1', '--format=%h', '--', 'runtime', 'translator'], cwd=ROOT,
                         capture_output=True, text=True).stdout.strip()
    return out or 'dev'


def needs(problems):
    found = set()
    for line in problems.splitlines():
        if 'android.webkit' in line: found.add('webview')
        elif 'DexClassLoader' in line: found.add('runtime-code')
        elif 'KeyPair' in line: found.add('rsa-keys')
        elif line.strip(): found.add('other')
    return sorted(found)


def build_runtime():
    """Compiles the runtime the extensions are translated against and the translator (as build.sh does), so a pulled
    change is in what's published."""
    os.makedirs(os.path.join(ROOT, 'build', 'translator'), exist_ok=True)
    mvn = lambda pom, out, *goals: subprocess.run(['./mvnw', '-q', '-B', '-f', pom, *goals, 'dependency:build-classpath',
                                                  f'-Dmdep.outputFile={os.path.join(ROOT, "build", out)}'], cwd=ROOT, check=True)
    mvn('runtime/pom.xml', 'runtime.cp', 'compile')
    mvn('translator/pom.xml', 'translator.cp')
    tcp = open(os.path.join(ROOT, 'build', 'translator.cp')).read().strip()
    sources = [os.path.join('translator', f) for f in os.listdir(os.path.join(ROOT, 'translator')) if f.endswith('.java')]
    subprocess.run(['javac', '-nowarn', '-cp', tcp, '-d', 'build/translator', *sources], cwd=ROOT, check=True)
    subprocess.run(['cp', '-R', 'translator/META-INF', 'build/translator/'], cwd=ROOT, check=True)


def translate(jars, jobs):
    out = os.path.join(WORK, 'out')
    os.makedirs(out, exist_ok=True)
    rcp = os.path.join(ROOT, 'runtime', 'target', 'classes') + ':' + open(os.path.join(ROOT, 'build', 'runtime.cp')).read().strip()
    tcp = os.path.join(ROOT, 'build', 'translator') + ':' + open(os.path.join(ROOT, 'build', 'translator.cp')).read().strip()
    chunks = [jars[i::jobs] for i in range(jobs) if jars[i::jobs]]
    procs = [subprocess.Popen(['java', '-Xmx3g', '-cp', tcp, 'Driver', rcp, out, *c], cwd=ROOT,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) for c in chunks]
    results = {}
    for p in procs:
        for line in p.stdout:
            if line.startswith('RESULT\t'):
                _, name, status, size, _ = line.rstrip('\n').split('\t')
                results[name] = status
                print(f'  {status:8} {name}', flush=True)
        p.wait()
    return out, results


def put(key, path, content_type, encoding=None):
    if TOKEN:
        data = open(path, 'rb').read()
        headers = {'Authorization': f'Bearer {TOKEN}', 'Content-Type': content_type, 'User-Agent': 'exia-mihon-converter'}
        if encoding: headers['X-Content-Encoding'] = encoding
        for attempt in range(5):
            try:
                urllib.request.urlopen(urllib.request.Request(f'{MIRROR}/{key}', data=data, method='PUT', headers=headers), timeout=120)
                return
            except (urllib.error.URLError, TimeoutError) as e:
                error = e
                time.sleep(2 ** attempt)
        raise SystemExit(f'upload failed: {key}: {error}')
    cmd = ['npx', 'wrangler', 'r2', 'object', 'put', f'{BUCKET}/{key}', '--file', path, '--content-type', content_type, '--remote']
    if encoding: cmd += ['--content-encoding', encoding]
    for attempt in range(4):
        r = subprocess.run(cmd, cwd=os.path.join(ROOT, '..', 'mihon-mirror'), capture_output=True, text=True)
        if r.returncode == 0: return
        time.sleep(2 ** attempt)
    raise SystemExit(f'upload failed: {key}\n{r.stderr[-2000:]}')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--langs', default='')
    ap.add_argument('--jobs', type=int, default=6)
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--no-upload', action='store_true')
    a = ap.parse_args()
    os.makedirs(os.path.join(WORK, 'jars'), exist_ok=True)
    state = json.load(open(STATE)) if os.path.exists(STATE) else {}
    if not state and not a.no_upload:
        # A fresh machine (the nightly run): what the mirror already has.
        try:
            raw = urllib.request.urlopen(urllib.request.Request(f'{MIRROR}/{REPO}/state.json', headers={'User-Agent': 'exia-mihon-converter'}), timeout=60).read()
            state = json.loads(gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw)
            print(f'state from the mirror: {len(state)} extensions')
        except urllib.error.HTTPError as e:
            if e.code != 404: raise
    engine = engine_version()
    repo, rows = read_index()
    langs = set(filter(None, a.langs.split(',')))
    if langs:
        rows = [r for r in rows if langs & {s['lang'] for s in r['sources']}]
    rows = [r for r in rows if r['jar'] and r['lib'].startswith('1.')]
    if a.limit: rows = rows[:a.limit]
    todo = [r for r in rows if state.get(r['pkg'], {}).get('key') != f"{r['versionCode']}-{engine}"]
    print(f'{repo["name"]}: {len(rows)} extensions, {len(todo)} to translate (engine {engine})', flush=True)

    def fetch(r):
        path = os.path.join(WORK, 'jars', r['jar'].rsplit('/', 1)[1])
        if not os.path.exists(path):
            urllib.request.urlretrieve(r['jar'], path + '.part')
            os.replace(path + '.part', path)
        return path
    if todo: build_runtime()
    done = 0
    # In batches, so a run that's stopped (a job's time limit) keeps what it finished.
    for b in range(0, len(todo), BATCH):
        batch = todo[b:b + BATCH]
        with concurrent.futures.ThreadPoolExecutor(16) as pool:
            jars = list(pool.map(fetch, batch))
        out, results = translate(jars, a.jobs)
        for r, jar in zip(batch, jars):
            key = f"{r['versionCode']}-{engine}"
            name = os.path.basename(jar)[:-4]
            js = os.path.join(out, name + '.js')
            # An extension that uses a class the runtime doesn't have yet (android.webkit.WebView) comes out empty. It's
            # noted, so it's tried again only when it or the engine changes.
            if results.get(name) not in ('ok', 'problems') or not os.path.exists(js) or os.path.getsize(js) == 0:
                state[r['pkg']] = {'key': key, 'skipped': results.get(name, 'missing')}
                print(f'  skipped  {name} ({results.get(name, "missing")})')
            else:
                data = open(js, 'rb').read()
                file = f"{r['pkg']}-{key}.js"
                gz = os.path.join(WORK, 'gz', file)
                os.makedirs(os.path.dirname(gz), exist_ok=True)
                open(gz, 'wb').write(gzip.compress(data, 9, mtime=0))
                problems_file = os.path.join(out, name + '.problems.txt')
                problems = open(problems_file).read() if os.path.exists(problems_file) else ''
                if not a.no_upload:
                    put(f'{REPO}/js/{file}', gz, 'text/javascript; charset=utf-8', 'gzip')
                state[r['pkg']] = {'key': key, 'file': file, 'size': len(data), 'gzSize': os.path.getsize(gz),
                                   'sha256': hashlib.sha256(data).hexdigest(), 'needs': needs(problems)}
                if not a.no_upload: os.remove(gz)
            for f in (js, os.path.join(out, name + '.problems.txt'), jar):
                if os.path.exists(f): os.remove(f)
            done += 1
        json.dump(state, open(STATE, 'w'), indent=1)
        if not a.no_upload:
            put(f'{REPO}/state.json', STATE, 'application/json')
        print(f'{done} of {len(todo)} translated', flush=True)

    exts = []
    for r in rows:
        s = state.get(r['pkg'])
        if not s or 'file' not in s: continue
        exts.append({k: r[k] for k in ('pkg', 'name', 'versionCode', 'versionName', 'lib', 'nsfw', 'icon', 'sources')}
                    | {'file': s['file'], 'size': s['size'], 'gzSize': s['gzSize'], 'sha256': s['sha256'], 'needs': s['needs']})
    index = repo | {'source': 'https://raw.githubusercontent.com/keiyoushi/extensions/repo/', 'engine': engine,
                    'generated': int(time.time()), 'extensions': sorted(exts, key=lambda e: e['name'].lower())}
    path = os.path.join(WORK, 'index.json')
    open(path, 'wb').write(gzip.compress(json.dumps(index, ensure_ascii=False, separators=(',', ':')).encode(), 9, mtime=0))
    if not a.no_upload:
        put(f'{REPO}/index.json', path, 'application/json; charset=utf-8', 'gzip')
        put(f'{REPO}/state.json', STATE, 'application/json')
    print(f'published {len(exts)} extensions' + (' (not uploaded)' if a.no_upload else ''))


if __name__ == '__main__':
    main()
