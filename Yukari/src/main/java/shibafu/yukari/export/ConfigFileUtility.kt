package shibafu.yukari.export

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonWriter
import java.io.StringWriter

abstract class ConfigFileMigrator<T> protected constructor(setup: MigratorBuilder.() -> Unit) {
    abstract val latestVersion: Int

    private val migrator: Map<Int, (MutableMap<String, Any>) -> Unit> = MigratorBuilder().let { it.setup(); it.migrator }

    fun toLatestDataObject(config: MutableMap<String, Any>, version: Int): T {
        if (version < latestVersion) {
            for (i in version..latestVersion - 1) {
                migrator[i]?.invoke(config)
            }
        }
        // TODO: POJOへのマッピング
        throw UnsupportedOperationException()
    }

    protected class MigratorBuilder {
        val migrator: MutableMap<Int, (MutableMap<String, Any>) -> Unit> = hashMapOf()

        /**
         * 指定のバージョンのコンフィグJSONに対するマイグレーション処理を定義します。
         * @param version 処理対象のバージョン
         * @param migrator マイグレーション処理
         */
        fun version(version: Int, migrator: (MutableMap<String, Any>) -> Unit) {
            this.migrator[version] = migrator
        }
    }
}

object ConfigFileUtility {
    private val filters: Map<Class<*>, ConfigFileMigrator<*>> = mapOf()

    fun <T> exportToJson(clazz: Class<T>, items: List<T>): String {
        val filter = filters[clazz] ?: return ""
        val writer = StringWriter()
        JsonWriter(writer).use {
            it.beginObject()

            it.name("version")
            it.value(filter.latestVersion)

            Gson().toJson(items, object : TypeToken<List<T>>(){}.type, it)

            it.endObject()
        }

        return writer.toString()
    }

    fun <T> importFromJson(clazz: Class<T>, json: String): List<T> {
        @Suppress("UNCHECKED_CAST")
        val filter = filters[clazz] as? ConfigFileMigrator<T> ?: throw IllegalArgumentException("invalid argument 'clazz' : $clazz")

        val decodedJson = Gson().fromJson(json, Map::class.java)
        if (!decodedJson.containsKey("version")) {
            throw IllegalArgumentException("invalid json : not contains key 'version'")
        }

        val version = decodedJson["version"].toString().toInt()
        val contents = decodedJson[clazz.simpleName] ?: throw IllegalArgumentException("invalid json : not contains key '${clazz.simpleName}'")

        @Suppress("UNCHECKED_CAST")
        if (contents is Map<*, *>) {
            return listOf(filter.toLatestDataObject(contents as MutableMap<String, Any>, version))
        } else if (contents is Collection<*>) {
            return contents.map { filter.toLatestDataObject(it as MutableMap<String, Any>, version) }
        }

        throw IllegalArgumentException("invalid json : invalid format key '${clazz.simpleName}'")
    }
}