"""
migrate_reference_data.py

One-time migration script that converts existing single-capture reference JSON files
to the new multi-capture format that supports multiple contributors per letter.

Old format:
    { "letter": "A", "landmarks": [...] }

New format:
    { "letter": "A", "captures": [ { "contributor": "variant1", "landmarks": [...] } ] }

Usage:
    python3 scripts/migrate_reference_data.py

This script is safe to run multiple times - it skips files already in the new format.
"""

import json
import os

REFERENCE_DIR       = "scripts/reference_data"
DEFAULT_CONTRIBUTOR = "variant1"


def is_already_migrated(data):
    """Returns True if the file is already in the new multi-capture format."""
    return "captures" in data


def migrate_file(path, letter):
    with open(path, "r") as f:
        data = json.load(f)

    if is_already_migrated(data):
        print(f"  SKIP  {letter}.json - already in new format")
        return

    # Wrap existing landmarks in new captures structure
    migrated = {
        "letter": letter,
        "captures": [
            {
                "contributor": DEFAULT_CONTRIBUTOR,
                "landmarks": data["landmarks"]
            }
        ]
    }

    with open(path, "w") as f:
        json.dump(migrated, f, indent=2)

    print(f"  OK    {letter}.json - migrated ({len(data['landmarks'])} landmarks -> contributor '{DEFAULT_CONTRIBUTOR}')")


def main():
    if not os.path.isdir(REFERENCE_DIR):
        print(f"ERROR: Reference data directory not found: {REFERENCE_DIR}")
        print("Run collect_reference_data.py first.")
        return

    print(f"Migrating reference data in: {REFERENCE_DIR}/\n")

    migrated_count = 0
    skipped_count  = 0

    for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
        path = os.path.join(REFERENCE_DIR, f"{letter}.json")
        if not os.path.exists(path):
            continue
        before = skipped_count
        migrate_file(path, letter)
        if skipped_count == before:
            migrated_count += 1
        else:
            skipped_count += 1

    print(f"\nDone! Migrated: {migrated_count}, Skipped (already new format): {skipped_count}")
    print(f"\nNew format example:")

    # Print an example of the new format from the first available file
    for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
        path = os.path.join(REFERENCE_DIR, f"{letter}.json")
        if os.path.exists(path):
            with open(path) as f:
                data = json.load(f)
            print(f"  {letter}.json -> letter='{data['letter']}', "
                  f"captures={len(data['captures'])}, "
                  f"contributors={[c['contributor'] for c in data['captures']]}")
            break


if __name__ == "__main__":
    main()