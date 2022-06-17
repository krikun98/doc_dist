package com.documentation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.io.File

data class StoredProductDocumentation(
    val productVersion: String,
    val initialPage: String,
) {
  companion object {
    val productListLocation = "docs/metadata.json"

    val mapper = jacksonObjectMapper()

    var productList: Map<String, StoredProductDocumentation>

    var mut: Mutex = Mutex()

    init {
      mapper.registerKotlinModule()
      productList = readProductList(productListLocation)
    }

    private fun readProductList(fileName: String): Map<String, StoredProductDocumentation> {
      val jsonString: String = File(fileName).readText(Charsets.UTF_8)
      return mapper.readValue(jsonString)
    }

    suspend fun updateProductList() {
      while (true) {
        mut.lock()
        productList = readProductList(productListLocation)
        mut.unlock()
        delay(30000)
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

