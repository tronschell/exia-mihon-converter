#!/usr/bin/env python3
"""Summarizes run-all.sh results: how far each extension got, why it stopped, and the timings of the ones that worked.
Usage: python3 harness/summarize.py build/run-jit.jsonl [more.jsonl ...]"""
import json, sys, statistics as st, collections

STEPS = ["popular", "search", "details", "chapters", "pages", "image"]

def reach(r):
    if r.get("error") and "popular" not in r: return "crash", r["error"]
    last, why = "loaded", None
    for s in STEPS:
        x = r.get(s)
        if x is None: break
        if x.get("error"):
            why = x["error"]
            if s == "search": continue  # some sources have no search for "a"; keep going
            break
        if s == "popular" and not x.get("count"): why = "no titles"; break
        if s == "chapters" and not x.get("count"): why = "no chapters"; last = "details"; break
        if s == "pages" and not x.get("count"): why = "no pages"; last = "chapters"; break
        if s == "image" and not x.get("isImage"): why = f"image status {x.get('status')}"; break
        last = s
    return last, why

def kind(why):
    if not why: return None
    w = why.lower()
    if "cloudflare" in w: return "Cloudflare check (needs Verify Website)"
    if "http error 403" in w or " 403" in w: return "HTTP 403"
    if "http error 404" in w or " 404" in w: return "HTTP 404 (site moved or down)"
    if "http error 5" in w or "status 5" in w: return "HTTP 5xx (site down)"
    if "unable to connect" in w or "typeerror: fetch" in w or "dns" in w or "connectionrefused" in w or "econn" in w or "failed to fetch" in w: return "site unreachable"
    if "timeout" in w: return "timeout"
    if "not found" in w or "unsupported" in w or "notimplemented" in w: return "missing piece in the engine"
    if "login" in w or "token" in w: return "needs an account"
    return "other: " + why[:90]

for path in sys.argv[1:]:
    rows = [json.loads(l) for l in open(path) if l.strip()]
    reached = collections.Counter(); kinds = collections.Counter(); ok = []
    for r in rows:
        last, why = reach(r)
        reached[last] += 1
        if last == "image": ok.append(r)
        else: kinds[kind(why) or "other"] += 1
    print(f"== {path}: {len(rows)} extensions")
    for s in ["crash", "loaded"] + STEPS: print(f"  stopped after {s:9} {reached[s]}") if s != "image" else print(f"  all the way to a page image: {reached[s]}")
    print("  why the others stopped:")
    for k, v in kinds.most_common(): print(f"    {v:4}  {k}")
    if ok:
        def med(key, f=lambda x: x): 
            vals = [f(r) for r in ok if f(r) is not None]
            return f"{st.median(vals):.0f}" if vals else "-"
        print(f"  working ones (median): load {med('l', lambda r: r.get('loadMs'))} ms, size {med('s', lambda r: r.get('size')/1024)} KB")
        for s in STEPS:
            print(f"    {s:9} total {med(s, lambda r: r.get(s, {}).get('ms'))} ms, engine CPU {med(s, lambda r: max(0, r.get(s, {}).get('cpuMs', 0)))} ms")
        print(f"  memory: process {med('m', lambda r: r.get('rssMB'))} MB, JS heap {med('h', lambda r: r.get('heapMB'))} MB")
        print("  working:", ", ".join(sorted(r["file"].replace("tachiyomi-", "").rsplit("-v", 1)[0] for r in ok)))
