package com.documentation.plugins

import com.documentation.ProductDocumentation
import com.documentation.StoredProductDocumentation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File

fun Application.configureRouting() {
  routing { route("/help") { getProduct() } }
}

val versionRegex = """^\d{4}\.\d+$""".toRegex()
val pageRegex = """.+\.html$""".toRegex()

fun Route.getProduct() {
  route("/{name}") {

    get ("/{tokens...}") {
      // the tokens can have a version, file path, subproduct - anything, so we have a parser
      val productName = call.parameters["name"].toString()
      val tokens = call.parameters.getAll("tokens")
      val doc = parseTokens(productName, tokens)
      if (doc == null) {
        call.respondText(text = "404: $productName not found",status = HttpStatusCode.NotFound)
        return@get
      }
      processProduct(doc)
    }
  }
}

private suspend fun parseTokens(productName: String, tokens: List<String>?): ProductDocumentation? {
  if (!StoredProductDocumentation.exists(productName)) {
    return null
  }
  if (tokens == null || tokens.isEmpty()) {
    // return default page
    val doc = StoredProductDocumentation.get(productName) ?: return null
    return ProductDocumentation(productName, doc.productVersion, doc.initialPage)
  }
  var version: String
  var page: String
  val firstToken = tokens[0]
  var restOfTokens = listOf<String>()
  if(tokens.size > 1) {
    restOfTokens = tokens.subList(1, tokens.size)
  }
  if (versionRegex.matches(firstToken)) {
    // version
    page = StoredProductDocumentation.getInitialPage(productName)
    version = firstToken
  } else if (pageRegex.matches(firstToken) && tokens.size == 1){
    version = StoredProductDocumentation.getDefaultVersion(productName)
    page = firstToken
  }
  else {
    // subproduct
    val subproductName = "${productName}/${firstToken}"
    if (StoredProductDocumentation.exists(subproductName)) {
      // possible recursion for multiple subproducts
      return parseTokens(subproductName, restOfTokens)
    } else {
      // if it doesn't exist - the token is part of a path
      page = StoredProductDocumentation.getInitialPage(productName)
      version = StoredProductDocumentation.getDefaultVersion(productName)
      restOfTokens = tokens.toMutableList()
    }
  }
  if (restOfTokens.isNotEmpty()) {
    // whatever's left over is a page path
    page = restOfTokens.joinToString("/")
  }
  return ProductDocumentation(productName, version, page)
}

private suspend fun PipelineContext<Unit, ApplicationCall>.processProduct(doc: ProductDocumentation) {
  val git: Git = Git.open(File("docs/${doc.productName}"))
  val treeId = git.repository.resolve("refs/heads/${doc.productVersion}^{tree}")
  if (treeId == null) {
    // if the version is invalid, get the default
    call.respondRedirect("/help/${doc.productName}")
    return
  }
  val treeWalk: TreeWalk? = TreeWalk.forPath(git.repository, doc.page, treeId)
  if (treeWalk == null) {
    // same for the file path
    call.respondRedirect("/help/${doc.productName}/${doc.productVersion}")
    return
  }
  val blobId: ObjectId = treeWalk.getObjectId(0)
  val objectReader = git.repository.newObjectReader()
  val objectLoader = objectReader.open(blobId)
  val bytes: ByteArray = objectLoader.bytes
  objectReader.close()
  call.respondBytes(bytes, ContentType.Text.Html)
}
