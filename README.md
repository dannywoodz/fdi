# fdi
Command line tool to find duplicate images in a directory structure.

# Synopsis

    fdi dir-1 [...dir-n]

# Description

Scans one or more directory trees for images to determine if there are duplicate images in the set as a whole.
"Duplicate" in this case means "visually similar", *not* identical.  An image can be considered a duplicate of
another even if it's a different size, if the subject matter is 'similar' (e.g. two photos taken in quick succession,
where someone blinks in one).

Once the analysis is complete, the 'candidate sets' are output to standard output, with filenames one per line, separated by tabs.

# Background
Originally writen in Clojure with core/async, I've translated the most recent versions to Rust.
This provides a significant speed boost while keeping memory manageable (even with large candidate sets;
the Clojure implementation was starting out give me heap exhaustion errors well before completing processing).

# License

GNU General Public License, v3+ (https://opensource.org/licenses/GPL-3.0)
