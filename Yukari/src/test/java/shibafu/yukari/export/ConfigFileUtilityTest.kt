package shibafu.yukari.export

import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class ConfigFileUtilityTest {
    companion object {
        @BeforeClass @JvmStatic fun beforeClass() {
            // ConfigFileUtility.filtersにテスト用のマイグレータを登録
            val filters = ConfigFileUtility::class.java.getDeclaredField("filters")
            filters.isAccessible = true
            filters.set(ConfigFileUtility, mapOf<Class<*>, ConfigFileMigrator<*>>(
                    ConfigTestEntity::class.java to TestMigrator()
            ))
        }
    }

    @Test fun exportToJsonTest() {
        val entity = ConfigTestEntity("abcde", 114514, 1919810)
        val json = ConfigFileUtility.exportToJson(ConfigTestEntity::class.java, listOf(entity))
        assertEquals("""{"version":2,"records":[{"str":"abcde","num":114514,"num2":1919810}]}""", json)
    }

    @Test fun importFromJsonTest() {
        throw NotImplementedError()
    }
}

/**
 * コンフィグマイグレーションのテスト用エンティティ
 */
data class ConfigTestEntity(val str: String, val num: Int, val num2: Int)

/**
 * コンフィグマイグレーションのテスト用マイグレータ
 */
class TestMigrator : ConfigFileMigrator<ConfigTestEntity> {
    override val latestVersion: Int = 2

    constructor() : super({
        version(2) {
            it["num2"] = it["num"]
            it["str"] = it["strold"]
        }
    })
}