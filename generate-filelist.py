import sys
import os
import glob
import hashlib

def sha256sum(filename, blocksize=65536):
    h = hashlib.sha256()
    with open(filename, 'rb') as f:
        while True:
            chunk = f.read(blocksize)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()

def collect_files(filelist_path):
    files_set = set()
    with open(filelist_path, 'r') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            # Expand wildcards using glob, case-sensitive
            for item in glob.glob(line, recursive=True):
                real_item = os.path.realpath(item)
                if os.path.isdir(real_item):
                    for root, _, files in os.walk(real_item):
                        for fname in files:
                            fullpath = os.path.realpath(os.path.join(root, fname))
                            if os.path.isfile(fullpath):
                                files_set.add(fullpath)
                elif os.path.isfile(real_item):
                    files_set.add(real_item)
    return sorted(files_set)  # Default sort is case-sensitive

def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} filelist.txt result_hashes.txt")
        sys.exit(1)

    filelist = sys.argv[1]
    result_file = sys.argv[2]

    files = collect_files(filelist)

    results = []
    for f in files:
        try:
            h = sha256sum(f)
            results.append((f, h))
            print(f"{h} *{f}")
        except Exception as e:
            print(f"Error hashing {f}: {e}", file=sys.stderr)

    # Case-sensitive sort by directory/filename (real path)
    results.sort(key=lambda x: x[0])

    # Write hashes to result file
    with open(result_file, "w") as out:
        for f, h in results:
            out.write(f"{h} *{f}\n")

    # Show result file path and its sha256
    abs_result = os.path.realpath(result_file)
    print(f"\nResult file: {abs_result}")
    result_hash = sha256sum(abs_result)
    print(f"SHA256 of result file: {result_hash} *{abs_result}")

if __name__ == '__main__':
    main()
