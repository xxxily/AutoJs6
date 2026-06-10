package org.autojs.autojs.runtime.api

import android.content.Context
import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.capability.ProjectCapabilitySecurity
import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.PFiles.getElegantPath
import org.autojs.autojs.pio.PFiles.read
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.tool.Func1
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs6.R
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.lang.IllegalArgumentException

/**
 * Created by Stardust on Jan 23, 2018.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Apr 15, 2025.
 */
class Files(private val scriptRuntime: ScriptRuntime) {

    val context: Context
        get() = scriptRuntime.uiHandler.applicationContext

    val sdcardPath: String
        get() = externalStoragePath

    // FIXME by Stardust on Oct 16, 2018.
    //  ! Is not correct in sub-directory?
    //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
    //  ! 子目录的处理不够准确吗?
    fun path(relativePath: String?): String? {
        relativePath ?: return null
        val cwd = cwd() ?: return null
        if (relativePath.startsWith(separator)) {
            return relativePath
        }
        var file = File(cwd)
        relativePath.split(separator).forEach { path ->
            when {
                path == ".." -> {
                    file = file.getParentFile() ?: return null
                }
                path != "." && path.isNotBlank() -> {
                    file = File(file, path)
                }
            }
        }
        return when (relativePath.endsWith(separator)) {
            true -> file.path + separator
            else -> file.path
        }
    }

    @Throws(IllegalArgumentException::class)
    fun nonNullPath(relativePath: String): String {
        return path(relativePath) ?: throw IllegalArgumentException(context.getString(R.string.error_resolved_path_for_a_relative_path_cannot_be_null))
    }

    fun cwd(): String? = scriptRuntime.engines.myEngine().cwd()

    @JvmOverloads
    fun open(path: String? = null, mode: String? = null, encoding: String? = null, bufferSize: Int? = null): PFileInterface {
        val resolved = path(path)
        if (mode.orEmpty().any { it == 'w' || it == '+' }) {
            guardFileMutation("files.open", resolved, "overwrite")
        }
        return PFiles.open(resolved, mode, encoding, bufferSize)
    }

    fun create(path: String?): Boolean {
        return PFiles.create(path(path) ?: return false)
    }

    fun createIfNotExists(path: String?): Boolean {
        return PFiles.createIfNotExists(path(path) ?: return false)
    }

    fun createWithDirs(path: String?): Boolean {
        return PFiles.createWithDirs(path(path) ?: return false)
    }

    fun exists(path: String?): Boolean {
        return PFiles.exists(path(path))
    }

    fun ensureDir(path: String?): Boolean {
        return PFiles.ensureDir(path(path) ?: return false)
    }

    @JvmOverloads
    fun read(path: String?, encoding: String? = PFiles.DEFAULT_ENCODING): String {
        return PFiles.read(path(path), encoding)
    }

    @JvmOverloads
    fun readAssets(fileName: String?, encoding: String? = PFiles.DEFAULT_ENCODING): String {
        val niceFileName = ensureFileNameNotNull(fileName, ::readAssets.name)
        try {
            return read(context.assets.open(niceFileName), encoding)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun readBytes(path: String?): ByteArray {
        return PFiles.readBytes(path(path))
    }

    @JvmOverloads
    fun write(path: String?, text: String, encoding: String? = PFiles.DEFAULT_ENCODING) {
        val resolved = path(path)
        guardFileMutation("files.write", resolved, "overwrite")
        PFiles.write(resolved, text, encoding)
        auditFileMutation("files.write", resolved, "overwrite")
    }

    @JvmOverloads
    fun append(path: String?, text: String, encoding: String? = PFiles.DEFAULT_ENCODING) {
        PFiles.append(ensurePathNotNull(path, ::append.name), text, encoding)
    }

    fun appendBytes(path: String?, bytes: ByteArray?) {
        PFiles.appendBytes(ensurePathNotNull(path, ::appendBytes.name), bytes)
    }

    fun writeBytes(path: String?, bytes: ByteArray?) {
        val resolved = ensurePathNotNull(path, ::writeBytes.name)
        guardFileMutation("files.writeBytes", resolved, "overwrite")
        PFiles.writeBytes(resolved, bytes)
        auditFileMutation("files.writeBytes", resolved, "overwrite")
    }

    fun copy(pathFrom: String?, pathTo: String?): Boolean {
        val from = ensurePathNotNull(pathFrom, ::copy.name, "pathFrom")
        val to = ensurePathNotNull(pathTo, ::copy.name, "pathTo")
        if (PFiles.exists(to)) {
            guardFileMutation("files.copy", to, "overwrite")
        }
        return PFiles.copy(from, to).also { ok ->
            if (ok && PFiles.exists(to)) {
                auditFileMutation("files.copy", to, "overwrite")
            }
        }
    }

    fun renameWithoutExtension(path: String?, newName: String): Boolean {
        return PFiles.renameWithoutExtension(ensurePathNotNull(path, ::renameWithoutExtension.name), newName)
    }

    fun rename(path: String?, newName: String): Boolean {
        return PFiles.rename(ensurePathNotNull(path, ::rename.name), newName)
    }

    fun move(path: String?, newPath: String): Boolean {
        return PFiles.move(ensurePathNotNull(path, ::move.name), newPath)
    }

    fun getExtension(fileName: String?): String {
        return PFiles.getExtension(ensureFileNameNotNull(fileName, ::getExtension.name))
    }

    fun getName(filePath: String?): String {
        return PFiles.getName(ensurePathNotNull(
            pathToCheck = filePath,
            funcName = ::getName.name,
            pathArgName = "filePath",
            shouldWrapWithPathMethod = false,
        ))
    }

    fun getNameWithoutExtension(filePath: String?): String {
        return PFiles.getNameWithoutExtension(ensurePathNotNull(
            pathToCheck = filePath,
            funcName = ::getNameWithoutExtension.name,
            pathArgName = "filePath",
            shouldWrapWithPathMethod = false,
        ))
    }

    fun remove(path: String?): Boolean {
        val resolved = path(path)
        guardFileMutation("files.remove", resolved, "delete")
        return PFiles.remove(resolved).also { ok ->
            if (ok) auditFileMutation("files.remove", resolved, "delete")
        }
    }

    fun removeDir(path: String?): Boolean {
        val resolved = path(path)
        guardFileMutation("files.removeDir", resolved, "delete")
        return PFiles.removeDir(resolved).also { ok ->
            if (ok) auditFileMutation("files.removeDir", resolved, "delete")
        }
    }

    fun listDir(path: String?): Array<String> {
        return PFiles.listDir(path(path))
    }

    fun listDir(path: String?, filter: Func1<String, Boolean?>): Array<String> {
        return PFiles.listDir(path(path), filter)
    }

    fun isFile(path: String?): Boolean {
        return PFiles.isFile(path(path))
    }

    fun isDir(path: String?): Boolean {
        return PFiles.isDir(path(path))
    }

    fun isEmptyDir(path: String?): Boolean {
        return PFiles.isEmptyDir(path(path))
    }

    @JvmOverloads
    fun getHumanReadableSize(bytes: Long, useIecIdentifier: Boolean = false): String {
        return PFiles.getHumanReadableSize(bytes, useIecIdentifier)
    }

    fun formatSizeWithUnit(bytes: Long): String {
        return PFiles.formatSizeWithUnit(bytes)
    }

    fun getSimplifiedPath(path: String?): String {
        return getElegantPath(ensurePathNotNull(path, ::getSimplifiedPath.name, shouldWrapWithPathMethod = false))
    }

    private fun ensureFileNameNotNull(fileNameToCheck: String?, funcName: String, fileNameArgName: String = "fileName"): String {
        fileNameToCheck ?: throw WrappedIllegalArgumentException(
            context.getString(
                R.string.error_argument_name_for_class_name_and_member_func_name_cannot_be_nullish,
                fileNameArgName, Files::class.java.simpleName, funcName,
            )
        )
        return fileNameToCheck
    }

    private fun ensurePathNotNull(pathToCheck: String?, funcName: String, pathArgName: String = "path", shouldWrapWithPathMethod: Boolean = true): String {
        val path = if (shouldWrapWithPathMethod) path(pathToCheck) else pathToCheck
        path ?: throw WrappedIllegalArgumentException(
            context.getString(
                R.string.error_argument_name_for_class_name_and_member_func_name_cannot_be_nullish,
                pathArgName, Files::class.java.simpleName, funcName,
            )
        )
        return path
    }

    private fun guardFileMutation(api: String, path: String?, accessType: String) {
        val target = path ?: return
        ProjectCapabilitySecurity.guard(
            scriptRuntime = scriptRuntime,
            api = api,
            capabilities = listOf(CapabilityRegistry.STORAGE),
            riskLevel = "high",
            target = target,
            accessType = accessType,
        )
    }

    private fun auditFileMutation(api: String, path: String?, accessType: String) {
        val target = path ?: return
        ProjectCapabilitySecurity.audit(
            scriptRuntime = scriptRuntime,
            api = api,
            capabilities = listOf(CapabilityRegistry.STORAGE),
            riskLevel = "high",
            target = target,
            message = accessType,
        )
    }

    companion object {
        @JvmStatic
        fun join(parent: String?, vararg child: String?): String {
            return PFiles.join(parent, *child)
        }
    }
}
