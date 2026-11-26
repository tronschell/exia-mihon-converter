"""Downloads Keiyoushi's index (index.pb) and the .jar builds of every extension with a source in LANG.
Usage: python3 tools/fetch_extensions.py [lang=es] [out=jars]"""
import gzip, json, os, sys, urllib.request, concurrent.futures

HERE = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(HERE, 'pbcore.py')).read())
lang = sys.argv[1] if len(sys.argv) > 1 else 'es'
out = sys.argv[2] if len(sys.argv) > 2 else 'jars'
os.makedirs(out, exist_ok=True)
INDEX = 'https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb'
data = gzip.decompress(urllib.request.urlopen(INDEX).read())
top = parse(data)
store = [v for f, v in top if f == 101][0]
exts = [parse(v) for f, v in parse(store) if f == 1]
rows = []
for e in exts:
    g = lambda n: [v for f, v in e if f == n]
    srcs = [dict(parse(s)) for s in g(8)]
    langs = sorted({s.get(3, b'').decode() for s in srcs})
    if lang not in langs:
        continue
    res = dict(parse(g(3)[0]))
    rows.append(dict(name=g(1)[0].decode(), pkg=g(2)[0].decode(), lib=g(4)[0].decode(), version=g(6)[0].decode(),
                     jar=res.get(501, b'').decode(), nsfw=(g(7) or [0])[0], langs=langs, sources=len(srcs)))
json.dump(rows, open(os.path.join(out, 'index.json'), 'w'), indent=1)

def get(row):
    path = os.path.join(out, row['jar'].rsplit('/', 1)[1])
    if not os.path.exists(path):
        urllib.request.urlretrieve(row['jar'], path)
    return path

with concurrent.futures.ThreadPoolExecutor(16) as pool:
    list(pool.map(get, rows))
print(f'{len(rows)} extensions with {lang} sources in {out}/')
