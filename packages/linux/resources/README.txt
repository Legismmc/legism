Legism @version@ - portable build for Linux (@date@)

Run ./legism.sh to start the launcher. Everything it needs, Java included, lives in this
folder, and nothing is written outside it: game files land in ./game, so the whole folder
can be moved between disks or machines of the same architecture as one piece.

If the file will not start, mark it executable once:

    chmod +x legism.sh

Bundled runtimes cover x86_64 and aarch64. The script picks the one matching your machine,
and falls back to a java on your PATH if neither fits.

Source code and releases: https://github.com/Legismmc/legism
