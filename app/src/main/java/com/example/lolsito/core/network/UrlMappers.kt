package com.example.lolsito.core.network

/** The API serves some logos over plain http; Android blocks cleartext, so force https. */
fun String?.toHttps(): String? = this?.replaceFirst(Regex("^http://"), "https://")
