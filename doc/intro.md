# Introduction to fdi

FDI (Find Duplicate Images) uses the ImageMagick library to identify images
within a directory tree that appear to be duplicates.  This is a probabilistic
determination that's based on a pre-computed fingerprint of an image.  This
makes running this program from scratch against a large population of images
a compute-heavy activity.

# Design

## Directory Scanner

The directory scanner, fdi/scanner.clj, is responsible for walking the supplied
root directory and pushing the full, canonical paths of identified images onto
a queue.  When all images have been identified, the keyword :stop is written to
queue and the scanner stops.

## Cache Builder

The cache builder, fdi/cache.clj reads image names from a queue until the
retrieved message is the keyword :stop, at which point it terminates.  For each
filename received, a cache key (a string) and value (a map containing the
:fingerprint and :filename keys) is calculated

This key/value pair is written to an output queue for processing.

If an error is encountered while generating the fingerprint, the value is
written to the error queue.

The value of the :fingerprint key is :error.

When the atom :stop is read from the input queue, the keyword :no-more-images
is written to both the error and processing queues.

## Error Processor

The code in fdi/error.clj receives the filenames of images for which a
fingerprint could not be computed.

## Cache Analyser

fdi/analyser.clj reads from an input queue that feeds it fingerprints, until it reads the keyword :stop.  Each fingerprint is classified against the existing set as it is read.  When :stop is received, the user-supplied duplicate handler function is invoked once for each set of duplicate fingerprints identified.

The user-supplied function is invoked with a set of fingerprints
