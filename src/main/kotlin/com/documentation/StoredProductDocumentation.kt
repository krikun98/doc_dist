package com.documentation

import com.documentation.updater.GitWorker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

data class StoredProductDocumentation(val productVersion: String, val initialPage: String) {
  companion object {
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
        readProductList(productListLocation)
        gitWorker.updateModules(productList)
        mut.unlock()
        delay(updateFrequency * 60 * 1000)
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
