#!/usr/bin/env python3
"""Build a frequency dictionary for SymSpell from a hermitdave/FrequencyWords file.

The upstream files (https://github.com/hermitdave/FrequencyWords) are plain
``word count`` text files. This script lowercases every token, drops tokens with
digits or punctuation, merges case variants by summing their counts, sorts by
frequency and writes the requested number of entries as ``term<TAB>frequency``.

Example::

    curl -sSL -o de_full.txt \
        https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/de/de_full.txt
    python3 scripts/build-frequency-dictionary.py de_full.txt \
        symspell-service/src/main/resources/de-500k.txt --max-words 500000

The default keeps the raw counts. ``--transform`` offers ``log10`` and ``sqrt``
for experiments, but note that a monotone transform changes the ratio based cost
of the compound segmentation and the additive count logic of the split
heuristic; the ranking order itself is preserved.
"""

import argparse
import math
import re
import sys
from collections import defaultdict

LETTERS = "a-zäöüß"
PATTERN_PLAIN = re.compile("^[" + LETTERS + "]{%d,}$")
PATTERN_HYPHEN = re.compile("^[" + LETTERS + "-]{%d,}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input", help="FrequencyWords text file, one 'word count' pair per line")
    parser.add_argument("output", help="destination file, written as 'term<TAB>frequency'")
    parser.add_argument("--max-words", type=int, default=500000, help="number of entries to keep (default: 500000)")
    parser.add_argument("--min-count", type=int, default=1, help="drop entries below this raw count (default: 1)")
    parser.add_argument("--min-length", type=int, default=2, help="minimum token length (default: 2)")
    parser.add_argument("--allow-hyphen", action="store_true", help="keep tokens that contain a hyphen")
    parser.add_argument(
        "--transform",
        choices=("none", "log10", "sqrt"),
        default="none",
        help="frequency transform applied after merging (default: none)",
    )
    return parser.parse_args()


def transform(value: float, mode: str) -> float:
    if mode == "log10":
        return round(math.log10(value + 1.0), 4)
    if mode == "sqrt":
        return round(math.sqrt(value), 4)
    return float(value)


def build_counts(path: str, pattern: re.Pattern, min_count: int) -> dict:
    counts = defaultdict(int)
    skipped = 0
    with open(path, encoding="utf-8") as source:
        for line in source:
            line = line.rstrip("\n")
            if not line:
                continue
            word, separator, raw_count = line.partition(" ")
            if not separator or not raw_count.isdigit():
                skipped += 1
                continue
            word = word.lower()
            if not pattern.match(word):
                skipped += 1
                continue
            count = int(raw_count)
            if count < min_count:
                continue
            counts[word] += count
    if skipped:
        print(f"skipped {skipped} tokens with digits, punctuation or malformed counts", file=sys.stderr)
    return counts


def main() -> int:
    args = parse_args()
    pattern = PATTERN_HYPHEN if args.allow_hyphen else PATTERN_PLAIN
    pattern = re.compile(pattern.pattern % args.min_length)

    counts = build_counts(args.input, pattern, args.min_count)
    entries = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[: args.max_words]
    if not entries:
        print("no entries left after filtering", file=sys.stderr)
        return 1

    with open(args.output, "w", encoding="utf-8") as target:
        for word, count in entries:
            value = transform(count, args.transform)
            text = str(int(value)) if value.is_integer() else f"{value:.4f}".rstrip("0").rstrip(".")
            target.write(f"{word}\t{text}\n")

    print(
        f"wrote {len(entries)} entries to {args.output} "
        f"(first: {entries[0][0]}={entries[0][1]}, last: {entries[-1][0]}={entries[-1][1]})",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
