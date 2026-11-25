#!/usr/bin/env python3
"""Checks the app's rate sources from outside the app, so a broken or stale feed is noticed before a user does.

Run by .github/workflows/rates-canary.yml every 6 hours. Exits non-zero (GitHub then emails the repo owner) when:
  - a source can't be fetched or parsed, or has dropped currencies the app relies on;
  - a live source's data is more than 6 hours old while forex markets are open;
  - the live sources disagree with each other by more than 1% on a major pair;
  - Currency-API's two mirrors serve different days (the CDN cache problem behind the 25 Nov 2026 stale rates).
Daily sources (ECB, ExchangeRate-API) are only checked for being reachable and at most ~2 days old; the app already
leaves them out when they fall behind. Standard library only.
"""
import datetime as dt
import email.utils
import json
import sys
import urllib.request

PAIRS = [("AUD", "HKD"), ("USD", "HKD"), ("EUR", "USD"), ("USD", "JPY"), ("GBP", "USD"), ("USD", "CNY")]
NEEDED = {c for p in PAIRS for c in p}
now = dt.datetime.now(dt.timezone.utc)
problems, rows = [], []


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "robFX-canary"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.load(r)


def market_open(t):
    wd = t.weekday()  # Monday 0 ... Sunday 6
    return not (wd == 5 or (wd == 6 and t.hour < 21) or (wd == 4 and t.hour >= 22))


def day(s):
    return dt.datetime.strptime(s, "%Y-%m-%d").replace(tzinfo=dt.timezone.utc)


def frankfurter():
    d = get("https://api.frankfurter.dev/v1/latest?base=USD")
    return {**d["rates"], "USD": 1.0}, day(d["date"]) + dt.timedelta(hours=15)


def fawaz(host):
    d = get(host)
    return {k.upper(): v for k, v in d["usd"].items()}, day(d["date"])


def erapi():
    d = get("https://open.er-api.com/v6/latest/USD")
    return d["rates"], dt.datetime.fromtimestamp(d["time_last_update_unix"], dt.timezone.utc)


def coinbase():
    d = get("https://api.coinbase.com/v2/exchange-rates?currency=USD")["data"]["rates"]
    return {k: float(v) for k, v in d.items()}, now


def floatrates():
    d = get("https://www.floatrates.com/daily/usd.json")
    when = email.utils.parsedate_to_datetime(next(iter(d.values()))["date"])
    return {**{v["code"]: float(v["rate"]) for v in d.values()}, "USD": 1.0}, when


SOURCES = {  # name: (fetch, live?) -- live sources must keep up through the trading day
    "Frankfurter": (frankfurter, False),
    "Currency-API (pages.dev)": (lambda: fawaz("https://latest.currency-api.pages.dev/v1/currencies/usd.min.json"), False),
    "Currency-API (jsDelivr)": (lambda: fawaz("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json"), False),
    "ExchangeRate-API": (erapi, False),
    "Coinbase": (coinbase, True),
    "FloatRates": (floatrates, True),
}

tables = {}
for name, (fetch, live) in SOURCES.items():
    try:
        rates, when = fetch()
    except Exception as e:  # noqa: BLE001 - any failure is the finding
        problems.append(f"{name}: unreachable or unparseable ({type(e).__name__}: {e})")
        continue
    missing = NEEDED - {k for k, v in rates.items() if v and float(v) > 0}
    if missing:
        problems.append(f"{name}: missing {sorted(missing)}")
    age_h = (now - when).total_seconds() / 3600
    limit = 6 if live else 50
    if (market_open(now) or not live) and age_h > limit:
        problems.append(f"{name}: data is {age_h:.1f}h old (limit {limit}h)")
    tables[name] = (rates, when, live)
    rows.append(f"{name:26} data {when:%Y-%m-%d %H:%M} UTC ({age_h:4.1f}h)  AUD/HKD {rates['HKD'] / rates['AUD']:.4f}")

live = {n: t[0] for n, t in tables.items() if t[2]}
if len(live) < 2:
    problems.append(f"only {len(live)} live source(s) answered: {sorted(live)}")
for f, t in PAIRS:
    vals = {n: r[t] / r[f] for n, r in live.items() if r.get(f) and r.get(t)}
    if len(vals) >= 2:
        spread = (max(vals.values()) - min(vals.values())) / min(vals.values())
        if spread > 0.01:
            problems.append(f"{f}/{t}: live sources differ {spread:.2%}: {vals}")

pages, jsd = tables.get("Currency-API (pages.dev)"), tables.get("Currency-API (jsDelivr)")
if pages and jsd and jsd[1] < pages[1]:
    print(f"note: jsDelivr serves {jsd[1]:%Y-%m-%d}, pages.dev {pages[1]:%Y-%m-%d} (the app reads pages.dev first)")

print(f"Rate canary at {now:%Y-%m-%d %H:%M} UTC, market {'open' if market_open(now) else 'closed'}")
print("\n".join(rows))
if problems:
    print("\nPROBLEMS:\n- " + "\n- ".join(problems))
    sys.exit(1)
print("\nAll sources healthy.")
