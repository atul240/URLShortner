import concurrent.futures, time, urllib.request, json

# 1. Redirect test
def hit_redirect(i):
    try:
        req = urllib.request.Request("http://localhost:8080/load-test", method="GET")
        # Redirect follow mat kar, sirf 302 check kar
        opener = urllib.request.build_opener(urllib.request.HTTPHandler())
        opener.add_handler(urllib.request.HTTPRedirectHandler())
        with opener.open(req, timeout=5) as res:
            return res.status
    except urllib.error.HTTPError as e:
        return e.code  # 302 bhi yahan ayega
    except Exception as e:
        return 0

start = time.time()
with concurrent.futures.ThreadPoolExecutor(max_workers=50) as ex:
    results = list(ex.map(hit_redirect, range(5000)))
elapsed = time.time() - start
print(f"Total: {len(results)}, Time: {elapsed:.2f}s, RPS: {len(results)/elapsed:.0f}/sec")
print(f"302 count: {results.count(302)}, Failed: {results.count(0)}")
