package org.autojs.autojs.runtime.api.augment.storages

import android.content.Context.MODE_PRIVATE
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.storage.LocalStorage
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray

object Storages : Augmentable() {

    override val selfAssignmentFunctions = listOf(
        ::create.name,
        ::namespace.name,
        ::namespaceNames.name,
        ::removeNamespace.name,
        ::removeNamespaceSync.name,
        ::remove.name,
        ::removeSync.name,
        ::all.name,
        ::names.name,
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun create(args: Array<out Any?>): StorageNativeObject = ensureArgumentsOnlyOne(args) {
        createRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun createRhino(name: Any?): StorageNativeObject = when {
        name.isJsNullish() -> throw WrappedIllegalArgumentException("Argument for storages.create cannot be nullish")
        else -> StorageNativeObject(Context.toString(name))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun namespace(args: Array<out Any?>): StorageNativeObject = ensureArgumentsLength(args, 2) {
        val (name, namespace) = it
        namespaceRhino(name, namespace)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun namespaceRhino(name: Any?, namespace: Any?): StorageNativeObject = StorageNativeObject(namespaceStorageName(name, namespace))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun namespaceNames(args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
        namespaceNamesRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun namespaceNamesRhino(name: Any?): NativeArray {
        val prefix = namespaceStorageNamePrefix(name)
        return LocalStorage.getAllStorageNames()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toNativeArray()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeNamespace(args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
        val (name, namespace) = it
        removeRhino(namespaceStorageName(name, namespace))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeNamespaceSync(args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
        val (name, namespace) = it
        removeSyncRhino(namespaceStorageName(name, namespace))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun remove(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        removeRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeRhino(name: Any?) = when {
        name.isJsNullish() -> throw WrappedIllegalArgumentException("Argument for storages.remove cannot be nullish")
        else -> LocalStorage.NAME_PREFIX + Context.toString(name)
    }.let { globalContext.getSharedPreferences(it, MODE_PRIVATE).edit().clear().apply() }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeSync(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        removeSyncRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeSyncRhino(name: Any?) = when {
        name.isJsNullish() -> throw WrappedIllegalArgumentException("Argument for storages.remove cannot be nullish")
        else -> LocalStorage.NAME_PREFIX + Context.toString(name)
    }.let { globalContext.getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit() }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun all(args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
        LocalStorage.getAllStorages().toNativeArray()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun names(args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
        LocalStorage.getAllStorageNames().toNativeArray()
    }

    private fun namespaceStorageName(name: Any?, namespace: Any?): String {
        require(!namespace.isJsNullish()) { "Argument namespace for storages.namespace cannot be nullish" }
        val base = storageName(name)
        val ns = coerceString(namespace).trim()
        require(ns.isNotEmpty()) { "Argument namespace for storages.namespace cannot be empty" }
        return "${namespaceStorageNamePrefix(base)}$ns"
    }

    private fun namespaceStorageNamePrefix(name: Any?): String = "${storageName(name)}::"

    private fun storageName(name: Any?): String {
        require(!name.isJsNullish()) { "Argument name for storages namespace API cannot be nullish" }
        return coerceString(name).trim().also {
            require(it.isNotEmpty()) { "Argument name for storages namespace API cannot be empty" }
        }
    }

}
