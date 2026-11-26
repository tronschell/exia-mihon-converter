# exia-mihon-converter

Translates [Keiyoushi](https://github.com/keiyoushi/extensions)'s Mihon extensions (`.jar` builds) to JavaScript, so
Project Exia can run them on iPhone, iPad, Mac and Apple TV in the JavaScriptCore those devices already have. Each
extension becomes one JavaScript file with [TeaVM](https://teavm.org) (Apache-2.0), together with a small runtime: the
Mihon extension API, an OkHttp whose calls go to the app's network stack, and the Android pieces extensions touch.

A GitHub Actions run every night translates the extensions that changed and uploads them to Project Exia's mirror
(`https://mihon.projectexia.com/keiyoushi/index.json`). The extensions run on the device; only this translation runs here.

- `runtime/`: what an extension expects from its host, compiled together with it.
- `translator/`: `Driver` translates jars (`./build.sh jars/*.jar` writes `out/<name>.js` and `out/<name>.problems.txt`).
- `tools/publish.py`: reads Keiyoushi's `index.pb`, translates new and updated extensions in batches, uploads them, and
  writes the index last. Needs JDK 21. `--no-upload` translates without uploading.
- `harness/host.mjs`: plays the app (network with cookies, settings, crypto, images) and runs popular, search,
  details, chapters, pages and the first page image of one translated extension.

```sh
python3 tools/fetch_extensions.py es jars
./build.sh jars/tachiyomi-es.mangatv-v1.6.7.jar
harness/setup.sh
bun harness/host.mjs out/tachiyomi-es.mangatv-v1.6.7.js "one piece"
BUN_JSC_useJIT=0 bun harness/host.mjs out/...js    # JavaScriptCore without JIT, as in an iOS app
```

The workflow needs one secret, `EXIA_MIHON_UPLOAD_TOKEN`, the mirror's upload token.
