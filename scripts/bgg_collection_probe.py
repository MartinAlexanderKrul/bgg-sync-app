#!/usr/bin/env python3
"""
BGG Collection Status Probe

Verifies the undocumented geekcollection.php collection-status write call that
BggRepository.setCollectionStatus() drives. BGG publishes no write API for
collections, so this endpoint is reverse-engineered from the website's own
status checkboxes and needs to be confirmed against a real account.

DRY RUN by default — prints the exact form post without touching BGG.
Pass --execute to actually write, then verify and restore the previous status.

Usage:
    python bgg_collection_probe.py --game 13 --status wanttoplay
    python bgg_collection_probe.py --game 13 --status wanttoplay --execute

Credentials come from --config (same format as bgg_player_config.json) or from
the BGG_USERNAME / BGG_PASSWORD environment variables.
"""

import argparse
import json
import os
import sys
import time
import xml.etree.ElementTree as ET
from typing import Optional

import requests

STATUS_FIELDS = [
    "own",
    "prevowned",
    "fortrade",
    "want",
    "wanttoplay",
    "wanttobuy",
    "wishlist",
    "preordered",
]

session = requests.Session()
session.headers.update({"User-Agent": "BoardFlowCollectionProbe/1.0"})


def login(username: str, password: str) -> bool:
    resp = session.post(
        "https://boardgamegeek.com/login/api/v1",
        json={"credentials": {"username": username, "password": password}},
        timeout=30,
    )
    return resp.ok and "SessionID" in session.cookies


def fetch_collection_item(username: str, game_id: int) -> Optional[dict]:
    """Returns {'collid': str, <status flags>} for the game, or None if not in collection."""
    url = (
        "https://boardgamegeek.com/xmlapi2/collection"
        f"?username={requests.utils.quote(username)}&id={game_id}"
    )
    for attempt in range(5):
        resp = session.get(url, timeout=30)
        if resp.status_code == 202:
            time.sleep(2)
            continue
        resp.raise_for_status()
        root = ET.fromstring(resp.text)
        item = root.find("item")
        if item is None:
            return None
        status = item.find("status")
        result = {"collid": item.get("collid")}
        for field in STATUS_FIELDS:
            result[field] = (status.get(field) if status is not None else "0") or "0"
        return result
    raise RuntimeError("collection query kept returning 202 (queued)")


def build_form(game_id: int, collid: Optional[str], flags: dict, wishlist_priority: int) -> dict:
    form = {
        "ajax": "1",
        "action": "savedata",
        "objecttype": "thing",
        "objectid": str(game_id),
        "collid": collid or "",
        "fieldname": "status",
    }
    for field in STATUS_FIELDS:
        form[field] = "1" if flags.get(field) else "0"
    if form["wishlist"] == "1":
        form["wishlistpriority"] = str(wishlist_priority)
    return form


def save_status(game_id: int, form: dict) -> requests.Response:
    return session.post(
        "https://boardgamegeek.com/geekcollection.php",
        data=form,
        headers={
            "Referer": f"https://boardgamegeek.com/boardgame/{game_id}",
            "X-Requested-With": "XMLHttpRequest",
        },
        timeout=30,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Probe the BGG collection-status write call")
    parser.add_argument("--game", type=int, required=True, help="BGG game id (objectid)")
    parser.add_argument(
        "--status",
        default="wanttoplay",
        choices=STATUS_FIELDS,
        help="status flag to toggle on (default: wanttoplay)",
    )
    parser.add_argument("--wishlist-priority", type=int, default=3, choices=range(1, 6))
    parser.add_argument("--config", help="JSON file with bgg_username / bgg_password")
    parser.add_argument("--execute", action="store_true", help="actually write to BGG")
    parser.add_argument(
        "--no-restore",
        action="store_true",
        help="keep the new status instead of restoring the original",
    )
    args = parser.parse_args()

    username = os.environ.get("BGG_USERNAME")
    password = os.environ.get("BGG_PASSWORD")
    if args.config:
        with open(args.config) as handle:
            config = json.load(handle)
        username = config.get("bgg_username", username)
        password = config.get("bgg_password", password)
    if not username or not password:
        sys.exit("Missing credentials: pass --config or set BGG_USERNAME / BGG_PASSWORD")

    if not login(username, password):
        sys.exit("Login failed")
    print(f"Logged in as {username}")

    before = fetch_collection_item(username, args.game)
    if before is None:
        print(f"Game {args.game} is not in the collection — this will create a new entry")
        collid = None
        original_flags = {field: False for field in STATUS_FIELDS}
    else:
        collid = before["collid"]
        original_flags = {field: before[field] == "1" for field in STATUS_FIELDS}
        print(f"Current entry: collid={collid} " + " ".join(
            f"{field}={before[field]}" for field in STATUS_FIELDS
        ))

    new_flags = dict(original_flags)
    new_flags[args.status] = not original_flags[args.status]
    form = build_form(args.game, collid, new_flags, args.wishlist_priority)

    print("\nPOST https://boardgamegeek.com/geekcollection.php")
    for key, value in form.items():
        print(f"  {key}={value}")

    if not args.execute:
        print("\nDry run — pass --execute to send it.")
        return

    resp = save_status(args.game, form)
    print(f"\nHTTP {resp.status_code}")
    print(f"Response: {resp.text[:500]}")
    if not resp.ok:
        sys.exit("Write failed")

    # The XML API is cached separately from the site, so the change can lag a few seconds.
    time.sleep(3)
    after = fetch_collection_item(username, args.game)
    if after is None:
        print("Verification: game is no longer in the collection")
    else:
        print(f"Verification: collid={after['collid']} " + " ".join(
            f"{field}={after[field]}" for field in STATUS_FIELDS
        ))
        expected = "1" if new_flags[args.status] else "0"
        matched = after[args.status] == expected
        print(f"{args.status} expected={expected} actual={after[args.status]} -> "
              f"{'OK' if matched else 'MISMATCH (or XML API lag)'}")

    if args.no_restore:
        return
    restore_collid = (after or {}).get("collid") or collid
    restore = save_status(args.game, build_form(
        args.game, restore_collid, original_flags, args.wishlist_priority
    ))
    print(f"Restored original status: HTTP {restore.status_code}")


if __name__ == "__main__":
    main()
