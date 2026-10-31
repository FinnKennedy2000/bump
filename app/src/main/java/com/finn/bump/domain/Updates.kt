package com.finn.bump.domain

private val BUILD_TAG = Regex("""build-(\d{1,9})""")

/** `build-12` -> 12; anything else -> null. */
fun buildNumber(tag: String): Int? = BUILD_TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toInt()

/** True only for a well-formed tag strictly above the installed versionCode. */
fun isNewer(installed: Int, tag: String): Boolean = (buildNumber(tag) ?: Int.MIN_VALUE) > installed
