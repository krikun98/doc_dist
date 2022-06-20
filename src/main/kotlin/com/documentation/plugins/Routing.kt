package com.documentation.plugins

import com.documentation.ProductDocumentation
import com.documentation.StoredProductDocumentation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import java.io.File
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

fun Application.configureRouting(documentationRepository: String) {
  routing { route("/help") { getProduct(documentationRepository) } }
}

val versionRegex = """^\d{4}\.\d+$""".toRegex()
val pageRegex = """.+\.html$""".toRegex()

fun Route.getProduct(documentationRepository: String) {
  route("/{name}") {
    get("/{tokens...}") {
      // the tokens can have a version, file path, subproduct - anything, so they're run through a
      // parser
      val productName = call.parameters["name"].toString()
      val tokens = call.parameters.getAll("tokens")
      val doc = parseTokens(productName, tokens)
      if (doc == null) {
        call.respondText(text = "404: $productName not found", status = HttpStatusCode.NotFound)
        return@get
      }
      // Explicit redirects are needed for navigation between static files to work
      // Parser is run twice on the first redirect, but subsequent ones are more efficient
      if (tokens == null || tokens.isEmpty() || !pageRegex.matches(tokens.last())) {
        if (doc.productVersion == StoredProductDocumentation.getDefaultVersion(productName)) {
          call.respondRedirect("/help/${doc.productName}/${doc.page}")
        } else {
          call.respondRedirect("/help/${doc.productName}/${doc.productVersion}/${doc.page}")
        }
        return@get
      }
      processProduct(documentationRepository, doc)
    }
  }
}

private suspend fun parseTokens(productName: String, tokens: List<String>?): ProductDocumentation? {
  // If we don't have this product on record - we return a 404
  if (!StoredProductDocumentation.exists(productName)) {
    return null
  }
  if (tokens == null || tokens.isEmpty()) {
    // return default page
    val doc = StoredProductDocumentation.get(productName) ?: return null
    return ProductDocumentation(productName, doc.productVersion, doc.initialPage)
  }
  val version: String
  var page: String
  val firstToken = tokens[0]
  var restOfTokens = listOf<String>()
  if (tokens.size > 1) {
    restOfTokens = tokens.subList(1, tokens.size)
  }
  if (versionRegex.matches(firstToken)) {
    // version, default page is shown
    page = StoredProductDocumentation.getInitialPage(productName)
    version = firstToken
  } else if (pageRegex.matches(firstToken) && tokens.size == 1) {
    // page under a product, shown with a default version
    version = StoredProductDocumentation.getDefaultVersion(productName)
    page = firstToken
  } else {
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

private suspend fun PipelineContext<Unit, ApplicationCall>.processProduct(
    documentationRepository: String,
    doc: ProductDocumentation
) {
  val defaultVersionForProduct = StoredProductDocumentation.getDefaultVersion(doc.productName)
  if (doc.productVersion == defaultVersionForProduct) {
    processProductWithFilesystem(documentationRepository, doc)
  } else {
    processProductWithGit(documentationRepository, doc)
  }
}

// Filesystem is storing the current version, no need to use git here
private suspend fun PipelineContext<Unit, ApplicationCall>.processProductWithFilesystem(
    documentationRepository: String,
    doc: ProductDocumentation
) {
  val filePath = "$documentationRepository/${doc.productName}/${doc.page}"
  val docFile = File(filePath)
  if (!(docFile.exists() && docFile.isFile)) {
    // if the file does not exist, get the default
    call.application.log.info(
        "File ${doc.page} does not exist for ${doc.productName} at version ${doc.productVersion}, redirecting")
    call.respondRedirect("/help/${doc.productName}")
    return
  }
  call.application.log.info("Serving file $docFile via filesystem")
  call.respondFile(docFile)
}

// Non-current versions are obtained with git show
private suspend fun PipelineContext<Unit, ApplicationCall>.processProductWithGit(
    documentationRepository: String,
    doc: ProductDocumentation
) {
  val repo: Repository = RepositoryBuilder()
    .setGitDir(File("$documentationRepository/${doc.productName}/.git"))
    .build()
  val treeId = repo.resolve("refs/heads/${doc.productVersion}^{tree}")
  if (treeId == null) {
    // if the version is invalid, get the default
    call.application.log.info(
        "Version ${doc.productVersion} does not exist for ${doc.productName}, redirecting")
    call.respondRedirect("/help/${doc.productName}")
    return
  }
  val treeWalk: TreeWalk? = TreeWalk.forPath(repo, doc.page, treeId)
  if (treeWalk == null) {
    // same for the file path
    call.application.log.info(
        "File ${doc.page} does not exist for ${doc.productName} at version ${doc.productVersion}, redirecting")
    call.respondRedirect("/help/${doc.productName}/${doc.productVersion}")
    return
  }
  val blobId: ObjectId = treeWalk.getObjectId(0)
  val objectReader = repo.newObjectReader()
  val objectLoader = objectReader.open(blobId)
  val bytes: ByteArray = objectLoader.bytes
  objectReader.close()
  call.application.log.info(
      "Serving file ${doc.page} for ${doc.productName} at version ${doc.productVersion} via git")
  call.respondBytes(bytes, ContentType.Text.Html)
}
