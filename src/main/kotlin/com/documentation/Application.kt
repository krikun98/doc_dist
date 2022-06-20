package com.documentation

import ch.qos.logback.classic.Logger
import com.documentation.plugins.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("trackerMain") as Logger

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
              shortName = "f")
          .default(DEFAULT_UPD_FREQ)
  val documentationRepository by
      parser
          .option(
              ArgType.String,
              description = "Directory of documentation repository",
              shortName = "d")
          .default(DEFAULT_REPO_DIR)
  val repositoryOrigin by
      parser
          .option(
              ArgType.String,
              description = "Origin URL of documentation repository",
              shortName = "o")
          .default("")
  try {
    parser.parse(args)

    embeddedServer(Netty, port = port, host = host) {
          configureHTTP()
          configureRouting(documentationRepository)
        }
        .start(wait = true)
  } catch (e: Exception) {
    log.error("Error! ${e.message}", e)
    println("Error! ${e.message}")
  }
  log.info("Documentation server is shutting down")
}
