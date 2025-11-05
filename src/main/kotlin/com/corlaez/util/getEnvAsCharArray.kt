package com.corlaez.util

public fun getEnvAsCharArray(key: String): CharArray? {
    return System.getenv(key)?.toCharArray()
}
