package com.nohimazin.monotune.lyrics

/**
 * Thrown (as a [Result.failure]) by a [LyricsProvider] when the provider has no
 * matching lyrics for the requested track but the absence is expected (e.g. the
 * track has no Monochrome match in the DB).
 *
 * [LyricsHelper.getRemoteLyrics] suppresses [reportException] calls for this type
 * so that routine "no match" outcomes don't flood logcat with stack traces.
 */
class LyricsNotFoundException(message: String) : Exception(message)

