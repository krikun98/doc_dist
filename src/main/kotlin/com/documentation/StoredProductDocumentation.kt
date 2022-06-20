package com.documentation

import ch.qos.logback.classic.Logger
import com.documentation.updater.GitWorker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory

data class StoredProductDocumentation(val productVersion: String, val initialPage: String) {
  companion object {
    val log: Logger = LoggerFactory.getLogger("ProductList") as Logger

    private val mapper = jacksonObjectMapper()

    private var productList: Map<String, StoredProductDocumentation> = HashMap()

    var mut: Mutex = Mutex()

    init {
      mapper.registerKotlinModule()
    }

    fun readProductList(productListLocation: String) {
      val jsonString: String = File("$productListLocation/$METADATA_FILE").readText(Charsets.UTF_8)
      productList = mapper.readValue(jsonString)
    }

    suspend fun updateProductList(
        gitWorker: GitWorker,
        productListLocation: String,
        updateFrequency: Long
    ) {
      while (true) {
        mut.lock()
        log.debug("locked mutex, updating")
        readProductList(productListLocation)
        gitWorker.updateModules(productList)
        mut.unlock()
        log.debug("unlocked mutex, waiting")
        Thread.sleep(updateFrequency * 60000L) // delay lets go of the task and never gets back to it for some reason
        log.debug("finished delay")
      }
    }

    suspend fun get(productName: String): StoredProductDocumentation? {
      mut.lock()
      val doc = productList[productName]
      mut.unlock()
      if (doc != null) {
        return doc
      }
      return null
    }

    suspend fun getDefaultVersion(productName: String): String {
      val doc = get(productName)
      if (doc != null) {
        return doc.productVersion
      }
      return ""
    }

    suspend fun getInitialPage(productName: String): String {
      val doc = get(productName)
      if (doc != null) {
        return doc.initialPage
      }
      return ""
    }

    suspend fun exists(productName: String): Boolean {
      val doc = get(productName)
      if (doc != null) {
        return true
      }
      return false
    }
  }
}
