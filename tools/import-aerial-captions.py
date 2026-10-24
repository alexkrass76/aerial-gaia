#!/usr/bin/env python3
"""Import Russian/Ukrainian metadata from Apple's public resources-16.tar (no videos).
Usage: python3 tools/import-aerial-captions.py /path/to/resources-16.tar
Source: https://sylvan.apple.com/Aerials/resources-16.tar
"""
import json
import plistlib
from pathlib import Path
import sys
import tarfile
root = Path(__file__).resolve().parents[1]
existing = json.loads((root / 'app/src/main/assets/aerials.json').read_text())
with tarfile.open(sys.argv[1]) as archive:
    entries = {v['id']: v for v in json.load(archive.extractfile('entries.json'))['assets']}
    for language in ('ru', 'uk'):
        strings = plistlib.load(archive.extractfile(f'TVIdleScreenStrings.bundle/{language}.lproj/Localizable.nocache.strings'))
        result = {}
        for video in existing['videos']:
            points = entries[video['id']]['pointsOfInterest']
            result[video['id']] = [[int(second), strings[key].replace('\u00a0', ' ')] for second, key in sorted(points.items(), key=lambda p: int(p[0]))]
        (root / f'app/src/main/assets/aerial-captions-{language}.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(language, len(result), 'videos', sum(map(len,result.values())), 'captions')
