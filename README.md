# fdi - Find Duplicate Images

An application for finding duplicate images within a directory tree.

'Duplicate' in this sense means 'very similar', not 'identical'.  It's
therefore possible for images that are of differing dimensions to be
reported as duplicates, or where the subject matter varies only very
slightly (such as a re-take of a picture where a subject blinks).

# Implementation

This application is written in a mixture of Clojure (mainly), Java and
C.  It requires the ImageMagick library, in particular the MagickWand
C API, where the image 'fingerprinting' is done.

Clojure is for orchestration and concurrency, with Java for
performance-sensitive code and C for access to ImageMagick.

This application has been tested against a directory tree containing in
excess of 200,000 images.  A clean scan on a 3.4GHz Intel Core i7 2600K
running Linux takes ~1h20m.

## Usage

As an application:

  lein run /base/directory/for/images

As a library:

  (ns your-namespace
	  (:require [fdi/core :as fdi]))

	(fdi/scan "/base/directory/for/images" duplicate-handler-fn)

duplicate-handler-fn will be invoked once for each set of duplicates
identified, and will be provided with a list of fingerprints, where
each fingerprint is a hash conforming to the following structure:

  { :fingerprint byte[],
	  :id          String,
		:filename    String }

## License

Copyright © 2014 Daniel Woods (dannywoodz@yahoo.co.uk)

Distributed under the GNU General Public License, either version 3 or (at
your option) any later version.
