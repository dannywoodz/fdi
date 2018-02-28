# fdi
Command line tool to find duplicate images in a directory structure.

## Synopsis

    fdi [--threshold=5] dir-1 [...dir-n]

## Description

Scans one or more directory trees for images to determine if there are duplicate images in the set as a whole.
"Duplicate" in this case means "visually similar", *not* identical.  An image can be considered a duplicate of
another even if it's a different size, if the subject matter is 'similar' (e.g. two photos taken in quick succession,
where someone blinks in one).

Once the analysis is complete, the 'candidate sets' are output to standard output, with filenames one per line, separated by tabs.

## Building

`fdi` uses ImageMagick to do the heavy lifting of loading images and generating fingerprints.  `src/fdi.c` provides the functions
necessary to do this, and `build.rs` is responsible for building and linking.  You'll need ImageMagick 7 (with its development headers), a C compiler, and `pkg-config`.  Basically, if `pkg-config --cflags --libs MagickWand` works and points to an ImageMagick 7 install, you're good to go with `cargo build`.

## TODOs

Fingerprint scanning and generation is probably about as fast as it's likely to get, but the identification of duplicates is extremely
clumsy and is doing way more than it needs to.  This is largely to get around the problem of duplicates appearing in the output:
if image A has duplicates B and C, I don't want B to be output later with A and C, and then C with A and B.

This is really just a 'get it working' release, so I'll be sorting that out next.  If you want to submit a pull request, I'd very much
appreciate it.

## Background

Originally writen in Clojure with core/async, I've translated the most recent versions to Rust.
This provides a significant speed boost while keeping memory manageable (even with large candidate sets;
the Clojure implementation was giving me heap exhaustion errors well before completing processing).

## License

GNU General Public License, v3+ (https://opensource.org/licenses/GPL-3.0)
