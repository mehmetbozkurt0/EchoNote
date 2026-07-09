package com.echonote.echonote

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform