#!/usr/bin/env python3
"""
Simple concurrent load test for URLShortner (stdlib only).

Usage:
  python3 tests/load-test.py [--total 100000] [--concurrency 1000] [--base http://localhost:8080]

Mix: 80% redirects (GET /<code>), 20% creates (POST /api/shorten).
Reports status-code counts, throughput and latency percentiles.

Note: all requests come from one host, so the app sees one client IP, and
RateLimiterService counts them against the same keys. Unless those keys are
offset first, most requests return 429 and the numbers measure the limiter:

  docker exec url-shortner-redis redis-cli SET "rate:shorten:null" -1000000000 EX 900
  docker exec url-shortner-redis redis-cli SET "rate:redirect:172.19.0.1" -1000000000 EX 900
  # ... run the load test ...
  docker exec url-shortner-redis redis-cli DEL rate:shorten:null rate:redirect:172.19.0.1
"""
import argparse
import collections
import http.client
import json
import statistics
import threading
import time
import urllib.parse
import uuid
from concurrent.futures import ThreadPoolExecutor

_local = threading.local()


def conn_for(base):
    if not hasattr(_local, "conn"):
        u = urllib.parse.urlparse(base)
        _local.conn = http.client.HTTPConnection(u.hostname, u.port, timeout=30)
    return _local.conn


def do_request(base, method, path, body=None):
    headers = {}
    if body is not None:
        headers["Content-Type"] = "application/json"
    start = time.perf_counter()
    try:
        c = conn_for(base)
        c.request(method, path, body=body, headers=headers)
        r = c.getresponse()
        r.read()
        status = r.status
    except Exception as exc:  # connection reset, timeout, etc.
        _local.conn = None
        status = type(exc).__name__
    return status, (time.perf_counter() - start) * 1000


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--total", type=int, default=100000)
    ap.add_argument("--concurrency", type=int, default=1000)
    ap.add_argument("--base", default="http://localhost:8080")
    args = ap.parse_args()

    # One known code for redirect traffic.
    code = "load-" + uuid.uuid4().hex[:8]
    status, _ = do_request(args.base, "POST", "/api/shorten",
                           json.dumps({"longUrl": "https://example.com/load", "customCode": code}))
    if status != 201:
        raise SystemExit(f"setup failed, POST returned {status}")

    def job(i):
        if i % 5 == 0:  # 20% creates
            body = json.dumps({"longUrl": f"https://example.com/load/{i}"})
            return do_request(args.base, "POST", "/api/shorten", body)
        return do_request(args.base, "GET", f"/{code}")  # 80% redirects

    print(f"Running {args.total} requests, concurrency {args.concurrency} ...")
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(job, range(args.total)))
    elapsed = time.perf_counter() - started

    counts = collections.Counter(s for s, _ in results)
    latencies = sorted(ms for s, ms in results if isinstance(s, int))

    def pct(p):
        return latencies[min(len(latencies) - 1, int(len(latencies) * p))] if latencies else float("nan")

    print(f"\nElapsed:      {elapsed:.1f}s")
    print(f"Throughput:   {args.total / elapsed:.0f} req/s")
    print("Status codes:")
    for k, v in sorted(counts.items(), key=lambda kv: str(kv[0])):
        print(f"  {k}: {v}  ({v * 100 / args.total:.1f}%)")
    if latencies:
        print(f"Latency ms:   p50={pct(0.50):.1f}  p95={pct(0.95):.1f}  p99={pct(0.99):.1f}  "
              f"max={latencies[-1]:.1f}  mean={statistics.mean(latencies):.1f}")


if __name__ == "__main__":
    main()
