#!/bin/sh
# Starts Legism from its own directory using the bundled Java runtime.
#
# This is the Linux counterpart of LL.exe in the Windows portable build, and keeps to the
# same contract: find a runtime under jre/<arch>/bin/java, then hand it the arguments in
# stubLauncher.args with the package directory as the working directory. Everything after
# that - tl.bootargs, tl.args - is read by the bootstrap itself and needs nothing here.

set -eu

# Resolve the real directory of this script, following symlinks, so that a link dropped in
# ~/.local/bin or on the desktop still finds the runtime next to the actual files.
script=$0
while [ -L "$script" ]; do
    link=$(readlink "$script")
    case $link in
        /*) script=$link ;;
        *) script=$(dirname "$script")/$link ;;
    esac
done
base=$(cd "$(dirname "$script")" && pwd)
cd "$base"

# Prefer the host's own architecture, then anything else that might still run - the same
# order the Windows stub uses, for the same reason: an x64 runtime under emulation beats
# refusing to start.
case $(uname -m) in
    aarch64 | arm64) arch_stack="aarch64 x64" ;;
    x86_64 | amd64) arch_stack="x64" ;;
    *) arch_stack="x64 aarch64" ;;
esac

java=
for arch in $arch_stack; do
    candidate="$base/jre/$arch/bin/java"
    if [ -x "$candidate" ]; then
        java=$candidate
        break
    fi
done

if [ -z "$java" ]; then
    # Nothing bundled fits, but a system Java may still be new enough.
    if command -v java >/dev/null 2>&1; then
        java=$(command -v java)
    else
        echo "Legism: no usable Java runtime found." >&2
        echo "Looked for:" >&2
        for arch in $arch_stack; do
            echo "  $base/jre/$arch/bin/java" >&2
        done
        echo "  java on PATH" >&2
        exit 1
    fi
fi

# Read stubLauncher.args the way the Windows stub does: one argument per line, blank lines
# and # comments ignored, so the two packages can share the same file format.
set --
while IFS= read -r line || [ -n "$line" ]; do
    case $line in
        '' | \#*) continue ;;
    esac
    set -- "$@" "$line"
done < "$base/stubLauncher.args"

_JAVA_OPTIONS="-DstubLauncher=v1"
export _JAVA_OPTIONS

exec "$java" "$@"
