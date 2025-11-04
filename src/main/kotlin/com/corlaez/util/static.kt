package com.corlaez.util

import com.corlaez.gemini.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Static file serving configuration and handlers
 */

/**
 * MIME type mapper for Gemini protocol
 * Based on file extensions supported by Lagrange and other Gemini clients
 */
internal object GeminiMimeTypes {
    private val mimeTypeMap = mapOf(
        // Gemini
        "gmi" to "text/gemini",
        "gemini" to "text/gemini",

        // Text formats
        "txt" to "text/plain",
        "md" to "text/markdown",
        "markdown" to "text/markdown",
        "html" to "text/html",
        "htm" to "text/html",
        "xml" to "text/xml",
        "json" to "application/json",
        "css" to "text/css",
        "js" to "text/javascript",
        "csv" to "text/csv",

        // Images
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",

        // Audio
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/opus",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wma" to "audio/x-ms-wma",

        // Video
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "ogv" to "video/ogg",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "mkv" to "video/x-matroska",

        // Documents
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",

        // Fonts
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "woff" to "font/woff",
        "woff2" to "font/woff2",

        // Code
        "c" to "text/plain",
        "cpp" to "text/plain",
        "h" to "text/plain",
        "java" to "text/plain",
        "kt" to "text/plain",
        "py" to "text/plain",
        "rb" to "text/plain",
        "go" to "text/plain",
        "rs" to "text/plain",
        "sh" to "text/plain",
        "bash" to "text/plain",
    )

    /**
     * Get MIME type for a file extension
     */
    fun getMimeType(extension: String): String {
        return mimeTypeMap[extension.lowercase()] ?: "application/octet-stream"
    }

    /**
     * Get MIME type for a file
     */
    fun getMimeType(file: File): String {
        return getMimeType(file.extension)
    }

    /**
     * Get MIME type for a path
     */
    fun getMimeType(path: Path): String {
        return getMimeType(path.extension)
    }
}

/**
 * Static file serving configuration
 */
internal data class StaticFileConfig(
    val localPath: File,
    val urlPath: String,
    val indexFiles: List<String> = listOf("index.gmi", "index.gemini", "index.txt"),
    val listDirectories: Boolean = false,
    val maxFileSize: Long = 10 * 1024 * 1024 // 10MB default
)

/**
 * Static file handler
 */
internal class StaticFileHandler(private val config: StaticFileConfig) {

    init {
        require(config.localPath.exists()) {
            "Static file directory does not exist: ${config.localPath.absolutePath}"
        }
        require(config.localPath.isDirectory) {
            "Static file path is not a directory: ${config.localPath.absolutePath}"
        }
    }

    /**
     * Handle a static file request
     */
    suspend fun handle(context: RouteContext): GeminiResponse {
        val requestPath = context.path.removePrefix(config.urlPath).removePrefix("/")
        val file = resolveFile(requestPath)

        return when {
            file == null -> GeminiResponse.NotFound("File not found")
            !isPathSafe(file) -> GeminiResponse.BadRequest("Invalid path")
            file.isDirectory -> handleDirectory(file, requestPath)
            file.isFile -> handleFile(file)
            else -> GeminiResponse.NotFound("Not a file or directory")
        }
    }

    /**
     * Resolve requested path to actual file
     */
    private fun resolveFile(requestPath: String): File? {
        return try {
            val file = File(config.localPath, requestPath).canonicalFile
            if (!file.exists()) null else file
        } catch (e: Exception) {
            println("Error resolving file: ${e.message} (not propagated)")
            null
        }
    }

    /**
     * Check if the resolved path is safe (no directory traversal)
     */
    private fun isPathSafe(file: File): Boolean {
        val canonical = file.canonicalPath
        val base = config.localPath.canonicalPath
        return canonical.startsWith(base)
    }

    /**
     * Handle directory request
     */
    private fun handleDirectory(dir: File, requestPath: String): GeminiResponse {
        // Try to serve index file
        for (indexFile in config.indexFiles) {
            val indexPath = File(dir, indexFile)
            if (indexPath.exists() && indexPath.isFile) {
                return handleFile(indexPath)
            }
        }

        // List directory if enabled
        if (config.listDirectories) {
            return generateDirectoryListing(dir, requestPath)
        }

        return GeminiResponse.NotFound("Directory index not found")
    }

    /**
     * Handle file request
     */
    private fun handleFile(file: File): GeminiResponse {
        if (file.length() > config.maxFileSize) {
            return GeminiResponse.BadRequest("File too large")
        }

        return try {
            val mimeType = GeminiMimeTypes.getMimeType(file)
            val content = file.readText(Charsets.UTF_8)
            GeminiResponse.Success(mimeType = mimeType, body = content)
        } catch (e: Exception) {
            GeminiResponse.TemporaryFailure("Error reading file: ${e.message}")
        }
    }

    /**
     * Generate directory listing
     */
    private fun generateDirectoryListing(dir: File, requestPath: String): GeminiResponse {
        val files = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name }
        ) ?: emptyList()

        val urlBase = config.urlPath.removeSuffix("/")
        val currentPath = if (requestPath.isEmpty()) "" else "/$requestPath"

        val content = buildString {
            appendLine("# Directory: $currentPath")
            appendLine()

            // Parent directory link
            if (requestPath.isNotEmpty()) {
                val parentPath = requestPath.substringBeforeLast('/', "")
                val parentUrl = if (parentPath.isEmpty()) urlBase else "$urlBase/$parentPath"
                appendLine("=> $parentUrl/ ..")
            }

            // Files and directories
            for (file in files) {
                val fileName = file.name
                val fileUrl = "$urlBase$currentPath/$fileName"

                when {
                    file.isDirectory -> appendLine("=> $fileUrl/ 📁 $fileName/")
                    file.extension == "gmi" || file.extension == "gemini" ->
                        appendLine("=> $fileUrl 📄 $fileName")
                    file.extension in listOf("png", "jpg", "jpeg", "gif", "webp") ->
                        appendLine("=> $fileUrl 🖼️  $fileName")
                    file.extension in listOf("mp3", "ogg", "opus", "wav", "flac") ->
                        appendLine("=> $fileUrl 🎵 $fileName")
                    file.extension in listOf("mp4", "webm", "ogv") ->
                        appendLine("=> $fileUrl 🎬 $fileName")
                    else -> appendLine("=> $fileUrl 📄 $fileName")
                }
            }
        }

        return GeminiResponse.Success(body = content)
    }
}

/**
 * Extension functions for GeminiRouting to add static file serving
 */

/**
 * Serve static files from a directory
 *
 * Example:
 * ```
 * routing {
 *     staticFiles("/static", File("./public"))
 *     staticFiles("/images", File("./assets/images"), listDirectories = true)
 * }
 * ```
 */
public fun GeminiRouting.staticFiles(
    urlPath: String,
    localPath: File,
    indexFiles: List<String> = listOf("index.gmi", "index.gemini", "index.txt"),
    listDirectories: Boolean = false,
    maxFileSize: Long = 10 * 1024 * 1024
) {
    val config = StaticFileConfig(
        localPath = localPath,
        urlPath = urlPath.removeSuffix("/"),
        indexFiles = indexFiles,
        listDirectories = listDirectories,
        maxFileSize = maxFileSize
    )

    val handler = StaticFileHandler(config)

    // Register recursive wildcard route
    get("$urlPath/**") {
        handler.handle(this)
    }

    // Also handle exact path
    get(urlPath) {
        handler.handle(this)
    }
}

/**
 * Serve static files from a directory (String path variant)
 */
public fun GeminiRouting.staticFiles(
    urlPath: String,
    localPath: String,
    indexFiles: List<String> = listOf("index.gmi", "index.gemini", "index.txt"),
    listDirectories: Boolean = false,
    maxFileSize: Long = 10 * 1024 * 1024
) {
    staticFiles(urlPath, File(localPath), indexFiles, listDirectories, maxFileSize)
}

/**
 * Serve a single static file
 *
 * Example:
 * ```
 * routing {
 *     staticFile("/robots.txt", File("./public/robots.txt"))
 * }
 * ```
 */
public fun GeminiRouting.staticFile(urlPath: String, file: File) {
    get(urlPath) {
        if (!file.exists() || !file.isFile) {
            return@get GeminiResponse.NotFound("File not found")
        }

        try {
            val mimeType = GeminiMimeTypes.getMimeType(file)
            val content = file.readText(Charsets.UTF_8)
            GeminiResponse.Success(mimeType = mimeType, body = content)
        } catch (e: Exception) {
            GeminiResponse.TemporaryFailure("Error reading file: ${e.message}")
        }
    }
}

/**
 * Serve a single static file (String path variant)
 */
public fun GeminiRouting.staticFile(urlPath: String, localPath: String) {
    staticFile(urlPath, File(localPath))
}