package server

import com.google.gson.Gson
import git.GitImageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import system.SystemOps
import java.io.File

private val gson = Gson()

fun Application.configureApi(service: GitImageService, systemOps: SystemOps) {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        anyHost()
    }
    routing {
        get("/api/git/modified-images") {
            val repoPath = call.request.queryParameters["repoPath"] ?: ""
            call.respond(gson.toJson(service.getModifiedImages(repoPath)))
        }
        get("/api/git/image-data") {
            val repoPath = call.request.queryParameters["repoPath"] ?: ""
            val filePath = call.request.queryParameters["filePath"] ?: ""
            call.respond(gson.toJson(service.getImageData(repoPath, filePath)))
        }
        get("/api/git/prefetch") {
            val repoPath = call.request.queryParameters["repoPath"] ?: ""
            call.respond(gson.toJson(mapOf("started" to service.startPrefetch(repoPath))))
        }
        get("/api/system/pick-repo-folder") {
            call.respond(gson.toJson(mapOf("path" to systemOps.pickRepoFolder())))
        }
        get("/api/system/pick-manual-folder") {
            val channel = call.request.queryParameters["channel"] ?: "A"
            call.respond(gson.toJson(mapOf("path" to systemOps.pickManualFolder(channel))))
        }
        get("/api/system/list-folder-images") {
            val folderPath = call.request.queryParameters["folderPath"] ?: ""
            call.respond(gson.toJson(systemOps.listImagesInFolder(folderPath)))
        }
        get("/api/system/pick-manual-image") {
            val channel = call.request.queryParameters["channel"] ?: "A"
            call.respond(gson.toJson(systemOps.pickManualImage(channel)))
        }
        get("/api/system/local-image") {
            val path = call.request.queryParameters["path"] ?: ""
            val file = runCatching { File(path).canonicalFile }.getOrNull()
            if (file == null || !file.exists() || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, gson.toJson(mapOf("error" to "file_not_found")))
                return@get
            }
            call.respondFile(file)
        }
        get("/api/system/reveal-in-finder") {
            val repoPath = call.request.queryParameters["repoPath"] ?: ""
            val filePath = call.request.queryParameters["filePath"] ?: ""
            systemOps.revealInFinder(repoPath, filePath)
            call.respond(gson.toJson(mapOf("ok" to true)))
        }

        // In packaged app, frontend/dist is the resource root so files are at classpath root
        // In dev, fall back to filesystem
        try {
            staticResources("/assets", "assets")
        } catch (_: Exception) {
            staticFiles("/assets", File("frontend/dist/assets"))
        }

        get("{...}") {
            // Packaged: index.html is at classpath root
            val resource = this::class.java.classLoader.getResource("index.html")
                // Dev fallback: under frontend/dist/ on classpath
                ?: this::class.java.classLoader.getResource("frontend/dist/index.html")
            if (resource != null) {
                call.respondText(resource.readText(), ContentType.Text.Html)
            } else {
                val file = File("frontend/dist/index.html")
                if (file.exists()) call.respondFile(file)
                else call.respondText("Frontend not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}
