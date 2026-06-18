package org.autojs.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.max

class LibDeployer(
    private val project: Project,
    private val name: String,
    private val downloadUrl: String,
) {
    private var sourceDir: String = File.separator
    private var sourceFile: File = project.file(File.separator)

    private var destDir: String = File.separator
    private var destFile: File = project.file(File.separator)

    private val cacheRootFile: File = project.file("cache").apply { mkdirs() }
    private val cacheFileName: String
    private val cacheFileExtensionName: String
    private val cacheFile: File
    private val trustedSha256: String

    private val shouldPrintProgress: Boolean
        get() = project.gradle.extra["platform"]?.let {
            it::class.java.getMethod("getShouldPrintProgress")
                .apply { isAccessible = true }
                .invoke(it)
        } == true

    init {
        val extracted = extractFileFromUrl(downloadUrl)
        cacheFileName = "${extracted.fileName}-[${generateShortMd5String(downloadUrl).lowercase()}]"
        cacheFileExtensionName = extracted.extensionName
        cacheFile = project.file(File(cacheRootFile, "$cacheFileName.$cacheFileExtensionName"))
        trustedSha256 = requireTrustedSha256(downloadUrl)
    }

    private fun getSkipFile(): File = project.file(File(destFile, "$cacheFileName.skip"))
    private fun getTempOutFile(): File = project.file(File(destFile, "temp-extracted"))

    fun setSourceDir(sourceDir: String): LibDeployer {
        this.sourceDir = sourceDir
        this.sourceFile = project.file(sourceDir)
        return this
    }

    fun setDestDir(destDir: String): LibDeployer {
        val dest = if (destDir.startsWith(File.separator)) {
            project.file(destDir.substring(1))
        } else {
            project.file(destDir)
        }
        dest.mkdirs()
        this.destDir = destDir
        this.destFile = dest
        return this
    }

    fun deploy() {
        val tempOutFile = getTempOutFile()
        if (tempOutFile.exists()) {
            project.delete(tempOutFile)
        }

        val (shouldDownload, shouldExtract) = checkCacheAndSkipFiles()

        var needExtract = shouldExtract
        if (shouldDownload) {
            printDownloadInfo()
            downloadWithRetry()
            requireTrustedSha256File(cacheFile)
            needExtract = true
        }

        if (needExtract) {
            printExtractInfo()
            try {
                extractCacheFile()
            } catch (e: Exception) {
                println()
                println("Cache file was deleted as there is an error during extraction")
                println("Cache file: ${cacheFile.absolutePath}")
                cacheFile.delete()
                e.message?.let { println("Error message: $it") }
                println()
                throw e
            }
            generateMd5File(cacheFile)
        }
    }

    fun clean() {
        project.delete(getSkipFile())
        project.delete(getTempOutFile())
        deleteDestAccordingToSrc()
        deleteCacheAccordingToMd5()
    }

    private data class ExtractedFile(val fileName: String, val extensionName: String)

    private fun extractFileFromUrl(url: String): ExtractedFile {
        val fileNameWithExtension = url.substringAfterLast('/')
        val dot = fileNameWithExtension.lastIndexOf('.')
        val fileNameRaw = if (dot >= 0) fileNameWithExtension.take(dot) else fileNameWithExtension
        val fileName = fileNameRaw.lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^a-z0-9.]"), "-")
        val extension = if (dot >= 0) fileNameWithExtension.substring(dot + 1) else ""
        return ExtractedFile(fileName, extension)
    }

    private fun checkCacheAndSkipFiles(): Pair<Boolean, Boolean> {
        var shouldDownload = true
        var shouldExtract = true

        val skip = getSkipFile()
        val trustedCacheExists = validateExistingTrustedCache(skip)
        if (skip.exists()) {
            if (trustedCacheExists) {
                println("No need to download or extract \"$name\" archive file as the \"skip file\" exists")
                shouldDownload = false
                shouldExtract = false
                println()
            } else {
                println("Skip file of \"$name\" was deleted because the trusted cache file is missing or invalid")
                println("Skip file: $skip")
                project.delete(skip)
                println()
            }
        } else if (cacheFile.exists()) {
            if (trustedCacheExists && validateMd5File(cacheFile)) {
                println("No need to download \"$name\" archive file as the cache file exists and is valid")
                shouldDownload = false
                println("Cache file of \"$name\" needs to be extracted as the \"skip file\" doesn't exist")
            } else {
                println("Cache file of \"$name\" was deleted as it is invalid")
                println("Cache file: $cacheFile")
                deleteCacheAndMd5()
            }
            println()
        }
        return shouldDownload to shouldExtract
    }

    private fun validateExistingTrustedCache(skip: File): Boolean {
        if (!cacheFile.exists()) {
            return false
        }
        if (validateTrustedSha256File(cacheFile)) {
            return true
        }
        println("Cache file of \"$name\" was deleted because its SHA-256 checksum is not trusted")
        println("Cache file: $cacheFile")
        deleteCacheAndMd5()
        if (skip.exists()) {
            project.delete(skip)
        }
        return false
    }

    private fun requireTrustedSha256(url: String): String {
        val checksumsFile = project.rootProject.file("gradle/third_party_checksums.toml")
        if (!checksumsFile.isFile) {
            throw GradleException("Missing third-party checksum file: ${checksumsFile.path}")
        }
        val checksums = parseTrustedSha256Checksums(checksumsFile)
        return checksums[url]?.lowercase(Locale.ROOT)
            ?: throw GradleException("Missing SHA-256 checksum for remote archive: $url")
    }

    private fun parseTrustedSha256Checksums(file: File): Map<String, String> {
        val entry = Regex("^\\s*\"([^\"]+)\"\\s*=\\s*\"([0-9a-fA-F]{64})\"\\s*(?:#.*)?$")
        return file.readLines()
            .mapNotNull { line -> entry.matchEntire(line)?.destructured }
            .associate { (url, sha256) -> url to sha256.lowercase(Locale.ROOT) }
    }

    private fun validateTrustedSha256File(file: File): Boolean {
        val actual = generateSha256String(file).lowercase(Locale.ROOT)
        val expected = trustedSha256.lowercase(Locale.ROOT)
        val matches = actual == expected
        if (matches) {
            println("SHA-256 verified: $actual")
        } else {
            println("SHA-256 checksum mismatch for \"$name\" archive file")
            println("Source: $downloadUrl")
            println("Expected: $expected")
            println("Actual: $actual")
        }
        return matches
    }

    private fun requireTrustedSha256File(file: File) {
        if (!validateTrustedSha256File(file)) {
            deleteCacheAndMd5()
            throw GradleException("SHA-256 checksum mismatch for \"$name\" archive file")
        }
    }

    private fun deleteCacheAndMd5() {
        project.delete(cacheFile)
        val md5File = File(cacheFile.parentFile, cacheFile.name + ".md5")
        if (md5File.exists()) {
            println("MD5 file of \"$name\" was deleted as it is unreliable")
            println("MD5 file: $md5File")
            project.delete(md5File)
        }
    }

    private fun validateMd5File(file: File): Boolean {
        val md5File = File(file.parentFile, file.name + ".md5")
        if (!md5File.exists()) return false
        val expected = md5File.readText().trim().uppercase()
        val actual = generateMd5String(file).uppercase()
        return expected == actual
    }

    private fun generateMd5File(file: File) {
        val md5File = File(file.parentFile, file.name + ".md5")
        println("Generating MD5...")
        val md5 = generateMd5String(file)
        md5File.writeText(md5)
        println("MD5 generated: $md5")
        println()
    }

    private fun generateMd5String(file: File): String {
        return generateDigestString(file, "MD5").padStart(32, '0')
    }

    private fun generateSha256String(file: File): String {
        return generateDigestString(file, "SHA-256").padStart(64, '0')
    }

    private fun generateDigestString(file: File, algorithm: String): String {
        FileInputStream(file).use { fis ->
            val channel: FileChannel = fis.channel
            val md = MessageDigest.getInstance(algorithm)
            val buffer = ByteBuffer.allocate(4096)
            while (channel.read(buffer) > 0) {
                buffer.flip()
                md.update(buffer)
                buffer.clear()
            }
            return BigInteger(1, md.digest()).toString(16).padStart(32, '0')
        }
    }

    private fun generateShortMd5String(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(s.toByteArray())
        return BigInteger(1, md.digest()).toString(32)
    }

    private fun printDownloadInfo() {
        val title = "Download \"$name\" archive file for \"${project.extensions.extraProperties["projectName"]}\" Gradle project"
        val srcInfo = "Source: $downloadUrl"
        val destInfo = "Destination: $cacheFile"
        val hintInfo = listOf(
            "If the download gets stuck and won't finish,",
            "try downloading the source file with tools like IDM (Internet Download Manager),",
            "then renaming it into the destination path above."
        )

        val maxLength = listOf(title, srcInfo, destInfo, *hintInfo.toTypedArray()).maxOf { it.length }
        listOf(
            "=".repeat(maxLength),
            title,
            "-".repeat(maxLength),
            srcInfo,
            destInfo,
            "-".repeat(maxLength),
            hintInfo.joinToString("\n"),
            "=".repeat(maxLength),
            ""
        ).forEach { println(it) }
    }

    private fun printExtractInfo() {
        val title = "Extract the archive file for \"${project.extensions.extraProperties["projectName"]}\" Gradle project"
        val srcInfo = "Source: $cacheFile"
        val destInfo = "Destination: $destFile"
        val items = listOf(title, srcInfo, destInfo)
        val maxLength = items.maxOf { it.length }
        listOf(
            "=".repeat(maxLength),
            title,
            "-".repeat(maxLength),
            srcInfo,
            destInfo,
            "=".repeat(maxLength),
            ""
        ).forEach { println(it) }
    }

    private fun downloadWithRetry(maxRetries: Int = 3, retryDelayMs: Long = 2000) {
        var attempt = 0
        var success = false
        while (attempt < maxRetries && !success) {
            try {
                attempt++
                download()
                success = true
            } catch (_: SocketTimeoutException) {
                println("Attempt $attempt/$maxRetries failed: Connection timed out. Retrying...")
            } catch (e: IOException) {
                println("Attempt $attempt/$maxRetries failed: ${e.message}. Retrying...")
            }
            if (!success) {
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelayMs)
                } else {
                    println("Download failed after $maxRetries attempts.")
                    throw GradleException("Download failed after $maxRetries attempts", null)
                }
            }
        }
    }

    private fun download() {
        cacheFile.parentFile.mkdirs()
        val urlConn = URI(downloadUrl).toURL().openConnection().apply {
            connectTimeout = 120_000
            readTimeout = 90_000
        }
        val fileSize = urlConn.contentLengthLong
        urlConn.getInputStream().use { input ->
            FileOutputStream(cacheFile).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                if (shouldPrintProgress && fileSize <= 0) {
                    println("\rDownloading...")
                }
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (shouldPrintProgress && fileSize > 0) {
                        val progress = downloaded * 100.0 / fileSize
                        val bar = generateProgressBar(progress)
                        print(String.format(Locale.getDefault(), "\rDownloading... [ %s ] %.2f%%", bar, progress))
                        System.out.flush()
                    }
                }
            }
        }
        val path = cacheFile.absolutePath
        if (fileSize > 0) {
            val formattedSize = formatFileSize(fileSize)
            print(String.format("\rDownload complete [ %s | %s ]\n", path, formattedSize))
        } else {
            print(String.format("\rDownload complete [ %s ]\n", path))
        }
        System.out.flush()
        println()
    }

    private fun extractCacheFile() {
        when (cacheFileExtensionName.lowercase()) {
            "zip" -> handleZip()
            "7z" -> handleSevenZip()
            else -> throw GradleException("Unknown archive file type: $cacheFileExtensionName")
        }
        println("All files extracted into [ $destFile ]")
        val skip = getSkipFile()
        if (!skip.exists()) {
            skip.parentFile.mkdirs()
            skip.createNewFile()
            println("File \"${skip.name}\" created")
        }
        println()
    }

    private fun handleZip() {
        val sourceDirPath = File(sourceDir).path.let { p ->
            if (p.startsWith(File.separator)) p.substring(1) else p
        }
        val tempOut = getTempOutFile()
        val tempOutCanonical = tempOut.canonicalFile
        val zipForTotal = ZipFile(cacheFile)
        val entriesAll = zipForTotal.entries()
        val entries = mutableListOf<ZipEntry>()
        var totalExtractedSize = 0L
        while (entriesAll.hasMoreElements()) {
            val entry = entriesAll.nextElement()
            val entryName = File(entry.name).path
            if (entryName.startsWith(sourceDirPath)) {
                entries += entry
                if (!entry.isDirectory) totalExtractedSize += entry.size
            }
        }
        zipForTotal.close()

        val zip = ZipFile(cacheFile)
        val zipEntries = zip.entries()
        var processed = 0
        val totalEntries = entries.size
        while (zipEntries.hasMoreElements()) {
            val entry = zipEntries.nextElement()
            val entryName = File(entry.name).path
            if (!entryName.startsWith(sourceDirPath)) continue
            val relativePath = entryName.substring(sourceDirPath.length)
            if (relativePath.isBlank()) {
                processed++
                continue
            }
            val outFile = safeArchiveOutputFile(tempOutCanonical, relativePath)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (shouldPrintProgress) {
                val progress = processed * 100.0 / max(1, totalEntries)
                val bar = generateProgressBar(progress)
                print(String.format(Locale.getDefault(), "\rExtracting... [ %s ] %.2f%%", bar, progress))
                System.out.flush()
            }
            processed++
        }
        val formatted = formatFileSize(totalExtractedSize)
        print(String.format("\rExtraction complete [ %s | %s ]\n", destFile.absolutePath, formatted))
        System.out.flush()
        println()
        zip.close()
        project.copy {
            from(tempOut)
            into(destFile)
        }
        project.delete(tempOut)
    }

    private fun safeArchiveOutputFile(root: File, relativePath: String): File {
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        if (normalized.isBlank() || normalized.split('/').any { it == ".." }) {
            throw GradleException("Unsafe archive entry path: $relativePath")
        }
        val outFile = File(root, normalized).canonicalFile
        val rootPath = root.canonicalPath + File.separator
        if (outFile.path != root.canonicalPath && !outFile.path.startsWith(rootPath)) {
            throw GradleException("Archive entry escapes extraction directory: $relativePath")
        }
        return outFile
    }

    private fun handleSevenZip() {
        val tempOut = getTempOutFile()
        val totalBytes = SevenZExtractor.extractDirectoryFrom7z(
            cacheFile, sourceDir, tempOut, shouldPrintProgress
        )
        val formatted = formatFileSize(max(totalBytes, 0L))
        print(String.format("\rExtraction complete [ %s | %s ]\n", destFile.absolutePath, formatted))
        System.out.flush()
        project.copy {
            from(tempOut)
            into(destFile)
        }
        project.delete(tempOut)
    }

    private fun deleteDestAccordingToSrc() {
        project.delete(destFile.absolutePath)
        var tmp: File? = destFile.parentFile
        while (tmp != null && tmp != project.projectDir) {
            val list = tmp.listFiles()?.toList().orEmpty()
            if (list.isEmpty()) {
                println("Delete empty directory: ${tmp.absolutePath}")
                project.delete(tmp)
            }
            tmp = tmp.parentFile
        }
    }

    private fun deleteCacheAccordingToMd5() {
        cacheRootFile.listFiles()?.forEach { f ->
            if (!f.name.endsWith(".md5")) {
                val md5 = File(f.parentFile, f.name + ".md5")
                if (!md5.exists() || !validateMd5File(f)) {
                    project.delete(f)
                    println("Delete cache file: ${f.absolutePath}")
                    if (md5.exists()) {
                        project.delete(md5)
                        println("Delete MD5 file: ${md5.absolutePath}")
                    }
                    println()
                }
            }
        }
    }

    private fun generateProgressBar(progress: Double, length: Int = 30): String {
        val filledLen = (length * progress / 100.0).toInt().coerceIn(0, length)
        val filled = "#".repeat(filledLen)
        val empty = "-".repeat(length - filledLen)
        return filled + empty
    }

    private fun formatFileSize(size: Long): String = when {
        // @formatter:off
        size < 1024L               -> String.format(Locale.getDefault(), "%d B",    size)
        size < 1024L * 1024        -> String.format(Locale.getDefault(), "%.2f KB", size / (1024.0))
        size < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MB", size / (1024.0 * 1024))
        else                       -> String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024 * 1024))
        // @formatter:on
    }

}
