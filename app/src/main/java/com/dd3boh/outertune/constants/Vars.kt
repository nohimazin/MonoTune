package com.dd3boh.outertune.constants

import android.os.Build
import com.dd3boh.outertune.BuildConfig

/**
 * Feature flags
 */

const val ENABLE_FFMETADATAEX = BuildConfig.FLAVOR == "full"


/**
 * Extra configuration
 */

// maximum concurrent image resolution jobs
const val MAX_COIL_JOBS = 16

// maximum concurrent download jobs allowed
const val MAX_DL_JOBS = 5


const val MAX_YTM_SYNC_JOBS = 3

// maximum concurrent scanner jobs allowed
const val MAX_YTM_CONTENT_JOBS = 16


/**
 * Constants
 */


const val LYRIC_FETCH_TIMEOUT = 60000L
const val SNACKBAR_VERY_SHORT = 2000L

/**
 * 5: pre 0.10.0-rc1
 * 6: 0.10.0-rc1 +
 */
const val OOBE_VERSION = 6


const val SYNC_CD = 60000 * 30

const val MAX_PLAYER_CONSECUTIVE_ERR = 3

/**
 * Misc weird constants
 */

val DEFAULT_PLAYER_BACKGROUND =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PlayerBackgroundStyle.BLUR else PlayerBackgroundStyle.GRADIENT


const val QUEUE_DEBUG = false
