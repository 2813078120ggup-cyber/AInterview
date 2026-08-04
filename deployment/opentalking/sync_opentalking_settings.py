#!/usr/bin/env python3
"""Merge portable OpenTalking settings without overwriting server networking."""

from __future__ import annotations

import argparse
import shutil
import sys
from datetime import datetime
from pathlib import Path

try:
    from dotenv import dotenv_values, set_key
except ImportError as exc:
    raise SystemExit(
        "python-dotenv is required. Run this script with the OpenTalking virtualenv Python."
    ) from exc


ALLOWED_EXACT = {"DASHSCOPE_API_KEY"}
ALLOWED_PREFIXES = (
    "OPENTALKING_LLM_",
    "OPENTALKING_TTS_",
    "OPENTALKING_STT_",
    "OPENTALKING_AGENT_",
    "OPENTALKING_MEMORY_",
    "OPENTALKING_PERSONA_",
)
DENIED_PREFIXES = (
    "OPENTALKING_WEBRTC_",
    "OPENTALKING_API_",
    "OPENTALKING_WEB_",
)


def portable_key(key: str) -> bool:
    if key.startswith(DENIED_PREFIXES):
        return False
    return key in ALLOWED_EXACT or key.startswith(ALLOWED_PREFIXES)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Merge local AI/TTS/STT/Agent settings into a server OpenTalking .env file."
    )
    parser.add_argument("--source", required=True, type=Path, help="Local settings .env uploaded to the server")
    parser.add_argument("--target", required=True, type=Path, help="Server OpenTalking .env")
    parser.add_argument("--dry-run", action="store_true", help="List keys without writing the target")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = args.source.expanduser().resolve()
    target = args.target.expanduser().resolve()
    if not source.is_file():
        raise SystemExit(f"Source file does not exist: {source}")
    if not target.is_file():
        raise SystemExit(f"Target file does not exist: {target}")
    if source == target:
        raise SystemExit("Source and target must be different files.")

    source_values = dotenv_values(source)
    selected = {
        key: value
        for key, value in source_values.items()
        if portable_key(key) and value is not None and value.strip()
    }
    skipped_empty = sorted(
        key for key, value in source_values.items()
        if portable_key(key) and (value is None or not value.strip())
    )
    if not selected:
        raise SystemExit("No non-empty portable OpenTalking settings were found in the source file.")

    print(f"Mode: {'DRY RUN' if args.dry_run else 'APPLY'}")
    print(f"Target: {target}")
    print("Keys selected (values are never printed):")
    for key in sorted(selected):
        print(f"  + {key}")
    if skipped_empty:
        print("Empty source keys skipped:")
        for key in skipped_empty:
            print(f"  - {key}")

    if args.dry_run:
        print(f"Dry run complete: {len(selected)} keys would be merged; server networking remains unchanged.")
        return 0

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = target.with_name(f"{target.name}.bak-{stamp}")
    shutil.copy2(target, backup)
    try:
        for key in sorted(selected):
            set_key(str(target), key, selected[key], quote_mode="always")
    except Exception:
        shutil.copy2(backup, target)
        raise

    print(f"Merged {len(selected)} keys successfully.")
    print(f"Backup: {backup}")
    print("Preserved: OPENTALKING_WEBRTC_*, OPENTALKING_API_*, OPENTALKING_WEB_* and server-only paths.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
