package uy.kohesive.injekt.api

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import java.lang.reflect.Type

class ExiaType(private val name: String) : Type {
    override fun getTypeName(): String = name
    override fun toString(): String = name
}

interface TypeReference<T> {
    val type: Type
}

abstract class FullTypeReference<T> : TypeReference<T> {
    override val type: Type = ExiaType(exia.js.Factory.types[javaClass.name] ?: "?")
}

interface InjektFactory {
    fun <R : Any> getInstance(forType: Type): R
    fun <R : Any> getInstanceOrElse(forType: Type, default: R): R = getInstance(forType)
}

object InjektScope : InjektFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstance(forType: Type): R = when (forType.typeName) {
        "android.app.Application", "android.content.Context" -> Application.shared
        "eu.kanade.tachiyomi.network.NetworkHelper" -> NetworkHelper.shared
        "kotlinx.serialization.json.Json" -> exia.runtime.Runner.json
        else -> exia.js.Factory.provide(forType.typeName) ?: throw IllegalStateException("Injekt can't provide ${forType.typeName}")
    } as R
}

inline fun <reified T : Any> fullType(): FullTypeReference<T> = object : FullTypeReference<T>() {}
