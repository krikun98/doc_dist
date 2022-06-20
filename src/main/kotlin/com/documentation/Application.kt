package com.documentation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.documentation.plugins.*
import com.documentation.updater.GitWorker
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

val log: Logger = LoggerFactory.getLogger("applicationMain") as Logger

fun main(args: Array<String>) {
  val parser = ArgParser("documentationServer")
  val port by
      parser
          .option(ArgType.Int, description = "Port to listen on", shortName = "p")
          .default(DEFAULT_PORT)
  val host by
      parser
          .option(ArgType.String, description = "Host link to listen on", shortName = "l")
          .default(DEFAULT_HOST)
  val updateFrequency by
      parser
          .option(
              ArgType.Int,
              description = "Documentation repository update frequency (in minutes)",
              shortName = "f"
          )
          .default(DEFAULT_UPD_FREQ)
    val repositoryPath by
    parser
        .option(
            ArgType.String,
            description = "Directory of documentation repository",
            shortName = "d"
        )
        .default(DEFAULT_REPO_DIR)
    val repositoryOrigin by
    parser
        .option(
            ArgType.String,
            description = "Origin URL of documentation repository",
            shortName = "o"
        )
        .default(DEFAULT_REPO_URL)
    log.level = Level.WARN
    try {
        parser.parse(args)

        val gitWorker = GitWorker(repositoryPath, repositoryOrigin)
        StoredProductDocumentation.readProductList(repositoryPath)

        thread(start = true) {
            runBlocking {
                launch {
                    StoredProductDocumentation.updateProductList(
                        gitWorker, repositoryPath, updateFrequency.toLong()
                    )
                }
            }
        }
        runBlocking {
            launch {
                embeddedServer(Netty, port = port, host = host) {
                    configureHTTP()
                    configureRouting(repositoryPath, gitWorker)
                }
                    .start(wait = true)
            }
        }
    } catch (e: Exception) {
    log.error("Error! ${e.message}", e)
    println("Error! ${e.message}")
  }
  log.info("Documentation server is shutting down")
}
