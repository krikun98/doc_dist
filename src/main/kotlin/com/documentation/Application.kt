package com.documentation

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.documentation.plugins.*
import kotlinx.coroutines.runBlocking


fun main() = runBlocking{
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureHTTP()
        configureRouting()
    }.start(wait = true)
    return@runBlocking
}
