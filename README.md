# fdi - Find Duplicate Images

An application for finding duplicate images within a directory tree.

'Duplicate' in this sense means 'very similar', not 'identical'.  It's
therefore possible for images that are of differing dimensions to be
reported as duplicates, or where the subject matter varies only very
slightly (such as a re-take of a picture where a subject blinks).

# Building the Native Dependency

The native library that interfaces with ImageMagick must be built
prior to running for the first time.  This needs pkg-config, and
the MagickWand C API.  To see if you have the required dependencies,
run 'pkg-config --cflags --libs MagickWand', which should print a list
of options and paths required.

With the MagickWand API available, all that's required is:

    cd src && make

This will install the native library into the resources folder.

Yes, I'm aware that Leiningen has its own view of where native
dependencies should go, but I haven't gotten around to making the
changes yet, and I'm really looking for something that would let
Leingingen *build* the C library, rather than just distributing it
as a binary blob.

# Usage

## As an application:

    lein run /base/directory/for/images

Supported command line options:

  * --no-cache -- disable loading or updating the cache.
  * --tolerance -- adjust the 'fudge factor' observed in fdi/FDI.java
  * --agents -- set the size of the agent pool used for fingerprint generation and analysis.

## As a library:

    (ns your-namespace
        (:require [fdi/core :as fdi]))

    (fdi/scan "/base/directory/for/images" duplicate-handler-fn)

duplicate-handler-fn will be invoked once for each set of duplicates
identified, and will be provided with a list of fingerprints, where
each fingerprint is a hash conforming to the following structure:

    { :fingerprint byte[],
      :id          String,
      :filename    String }

Overrides to the defaults can be provided with the arity-3 version of
fdi.core/scan, e.g.:

    (fdi/scan
        "/base/directory/for/images"
        duplicate-handler-fn
        {:disable-cache false :agent-count 5 :tolerance 5})

# Implementation

This application is written in a mixture of Clojure (mainly), Java and
C.  It requires the ImageMagick library, in particular the MagickWand
C API, where the image 'fingerprinting' is done.

Clojure is for orchestration and concurrency, with Java for
performance-sensitive code and C for access to ImageMagick.

This application has been tested against a directory tree containing in
excess of 200,000 images.  A clean scan on a 3.4GHz Intel Core i7 2600K
running Linux takes ~1h20m.

# TODO

## Fingerprint caching

This is currently handled via SQLite, but needs re-thinking.  In particular, the cache is loaded in its entirety up-front, which can actually take a significant amount of time if it has accumulated a large amount of data.

A better approach would be to clone the on-disk file into an in-memory database and run straight SQL against that.

## New Command line options

* --auto -- RISKY! -- automatically delete any images identified as duplicates.

# License

Copyright © 2014 Daniel Woods (dannywoodz@yahoo.co.uk)

Distributed under the GNU General Public License, either version 3 or (at
your option) any later version.
