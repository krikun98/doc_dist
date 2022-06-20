package com.documentation.updater

import com.documentation.StoredProductDocumentation
import kotlinx.coroutines.sync.Mutex
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File


class GitWorker(val repositoryPath: String, val repositoryOrigin: String) {
    private var git: Git
    private val versionRegex = """^.*(\d{4}\.\d+)$""".toRegex()
    private val mutexMap = HashMap<String, Mutex>()

    init {
        try {
            git = Git.open(File(repositoryPath))
        } catch (e: RepositoryNotFoundException) {
            git =
                Git.cloneRepository()
                    .setURI(repositoryOrigin)
                    .setDirectory(File(repositoryPath))
                    .setCloneSubmodules(true)
                    .setCloneAllBranches(true)
                    .call()
    }
    git.close()
  }

    suspend fun updateModules(productList: Map<String, StoredProductDocumentation>) {
        if (repositoryOrigin == "") {
            return
        }
        git = Git.open(File(repositoryPath))
        git.pull().call()
        val walk: SubmoduleWalk = SubmoduleWalk.forIndex(git.repository)
        while (walk.next()) {
            val submoduleRepository = walk.repository
            val submoduleName = walk.moduleName
            if (mutexMap[submoduleName] == null) {
                mutexMap[submoduleName] = Mutex()
            }
            mutexMap[submoduleName]?.lock() ?: return
            Git.wrap(submoduleRepository).fetch().call()
            val branches =
                Git.wrap(submoduleRepository)
                    .branchList()
                    .setListMode(ListBranchCommand.ListMode.REMOTE)
                    .call()
            for (branch in branches) {
                if ((versionRegex.matches(branch.name))) {
                    val versionResult = versionRegex.find(branch.name)
                    val version = versionResult?.groupValues?.get(1) ?: continue
          val ref: Ref? = submoduleRepository.exactRef("refs/heads/${version}")
          if (ref != null) {
            Git.wrap(submoduleRepository).checkout().setName(version).call()
            Git.wrap(submoduleRepository).pull().call()
          } else {
            Git.wrap(submoduleRepository)
                .checkout()
                .setName(version)
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .setCreateBranch(true)
                .call()
          }
                }
            }
            val submoduleVersionBranch = productList[submoduleName]?.productVersion ?: continue
            Git.wrap(submoduleRepository).checkout().setName(submoduleVersionBranch).call()
            submoduleRepository.close()

            mutexMap[submoduleName]?.unlock() ?: return
        }
    git.close()
  }

    suspend fun getPageForSubmodule(
        productName: String,
        pageName: String,
        productVersion: String
    ): ByteArray? {
        git = Git.open(File(repositoryPath))
        val walk: SubmoduleWalk = SubmoduleWalk.forIndex(git.repository)
        while (walk.next()) {
            val submoduleRepository = walk.repository
            val submoduleName = walk.moduleName
            if (submoduleName != productName) {
        continue
      }
            mutexMap[submoduleName]?.lock() ?: return null
      val treeId =
          submoduleRepository.resolve("refs/heads/$productVersion^{tree}")
              ?: throw InvalidVersionException()
            val treeWalk: TreeWalk =
                TreeWalk.forPath(submoduleRepository, pageName, treeId) ?: throw InvalidPageException()
            val blobId: ObjectId = treeWalk.getObjectId(0)
            val objectReader = submoduleRepository.newObjectReader()
            val objectLoader = objectReader.open(blobId)
            val bytes: ByteArray = objectLoader.bytes
            objectReader.close()
            git.close()

            mutexMap[submoduleName]?.unlock() ?: return null
            return bytes
        }
    return null
  }

  class InvalidVersionException : RuntimeException()
  class InvalidPageException : RuntimeException()
}
