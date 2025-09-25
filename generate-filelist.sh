#!/bin/bash

# Usage: ./hash_files.sh filelist.txt result_hashes.txt

set -euo pipefail

FILELIST="${1:-filelist.txt}"
RESULT="${2:-result_hashes.txt}"

# Temporary file to collect all real files
TMPFILES=$(mktemp)
trap 'rm -f "$TMPFILES"' EXIT

# Read filelist, ignore empty lines
while IFS= read -r file || [[ -n $file ]]; do
    [[ -z "$file" ]] && continue
    # Expand wildcards (case-sensitive)
    for entry in $file; do
        # If entry exists, resolve symlinks to realpath
        if [[ -e $entry ]]; then
            realentry=$(realpath "$entry")
            if [[ -d $realentry ]]; then
                # Directory: find all files recursively (case-sensitive)
                find "$realentry" -type f -print0
            elif [[ -f $realentry ]]; then
                printf "%s\0" "$realentry"
            fi
        fi
    done
done < "$FILELIST" | tr '\0' '\n' | LC_COLLATE=C sort -u > "$TMPFILES"

# Prepare result file
> "$RESULT"

# For each file, calculate sha256sum, output hash and real path, show during processing
while IFS= read -r realfile; do
    if [[ -f "$realfile" ]]; then
        hash=$(sha256sum -b "$realfile" | awk '{print $1}')
        echo "$hash *$realfile"
        echo "$hash *$realfile" >> "$RESULT"
    fi
done < <(LC_COLLATE=C sort -c "$TMPFILES" && cat "$TMPFILES")

# Case-sensitive sort by directory/filename
LC_COLLATE=C sort -k2,2 -o "$RESULT" "$RESULT"

echo
echo "Result file: $(realpath "$RESULT")"
echo "SHA256 of result file:"
sha256sum -b "$RESULT" | awk '{print $1 " " $2}'
