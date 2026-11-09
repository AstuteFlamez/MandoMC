#!/usr/bin/env python3
"""Offline, additive migration of explicit legacy ranks and integer-cent Essentials balances.

Read LuckPerms CSV exports (GROUP_PERMISSIONS is reference only); never read credentials or editor metadata.
The Java H2 CSVWRITE function can export LUCKPERMS_PLAYERS and LUCKPERMS_USER_PERMISSIONS from a COPY
of luckperms-h2-v2.mv.db. Keep CSVs and generated account files outside Git. Run with Paper stopped.
"""
import argparse
import csv
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
import time
from uuid import UUID

RANKS = {'mandostudios', 'mod', 'builder', 'creator', 'mandalore', 'marshal', 'warrior', 'default'}
MAX = 9_000_000_000_000_000


def memberships(players, permissions):
    result = {}
    for row in players:
        uid = str(UUID(row['UUID']))
        # Cached PRIMARY_GROUP is display metadata, not a grant; several legacy labels are stale.
        result[uid] = {'default'}
    for row in permissions:
        node = row['PERMISSION']
        if not node.startswith('group.'):
            continue  # Never migrate editor keys, arbitrary bypass grants or other private metadata.
        if row['SERVER'] != 'global' or row['WORLD'] != 'global' or row['CONTEXTS'] != '{}':
            raise ValueError('Contextual rank membership needs explicit review')
        if int(row['EXPIRY']) != 0:
            if int(row['EXPIRY']) <= time.time():
                continue
            raise ValueError('Temporary rank membership needs explicit review')
        uid = str(UUID(row['UUID']))
        rank = node[len('group.'):]
        if rank not in RANKS:
            raise ValueError('Unknown legacy rank: ' + rank)
        ranks = result.setdefault(uid, {'default'})
        if row['VALUE'].lower() == 'true':
            ranks.add(rank)
        else:
            ranks.discard(rank)
    return {uid: {'ranks': sorted(ranks or {'default'})} for uid, ranks in result.items()}


def balances(directories):
    result = {}
    for directory in directories:
        for file in sorted(directory.glob('*.yml')):
            try:
                uid = str(UUID(file.stem))
            except ValueError:
                continue
            match = re.search(r'^money:\s*[\'\"]?(-?\d+(?:\.\d+)?)[\'\"]?\s*$', file.read_text(), re.M)
            if not match:
                if re.search(r'^money:', file.read_text(), re.M):
                    raise ValueError('Unrecognized saved balance: ' + file.name)
                continue
            cents = Decimal(match[1]) * 100
            if cents != cents.to_integral_value() or not 0 <= cents <= MAX:
                raise ValueError('Balance requires explicit review: ' + file.name)
            result[uid] = int(cents)  # Later directory is the newer authoritative snapshot; never add duplicates.
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--players', type=Path, required=True)
    parser.add_argument('--memberships', type=Path, required=True)
    parser.add_argument('--essentials', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    files = [args.output / 'rank-users.yml', args.output / 'credits-data.yml']
    if any(file.exists() for file in files):
        raise SystemExit('Refusing to overwrite native account data. Import into a new staging directory.')
    with args.players.open() as players, args.memberships.open() as permissions:
        users = memberships(csv.DictReader(players), csv.DictReader(permissions))
    money = balances(args.essentials)
    args.output.mkdir(parents=True, exist_ok=True)
    files[0].write_text(json.dumps({'schema-version': 1, 'users': users}, indent=2) + '\n')
    files[1].write_text(json.dumps({'schema-version': 1, 'balances': money}, indent=2) + '\n')
    print(f'Staged {len(users)} rank accounts and {len(money)} credit balances. Original data unchanged.')


if __name__ == '__main__':
    main()
