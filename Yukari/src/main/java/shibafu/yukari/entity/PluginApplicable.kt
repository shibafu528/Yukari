package shibafu.yukari.entity

/**
 * プライバシー上の理由を考慮し、プラグインに情報を渡しても良いかを確認する手段を提供するインターフェース。
 */
interface PluginApplicable {
    /**
     * プライバシー上の理由を考慮し、プラグインに情報を渡しても良ければ真を返す。
     */
    val isApplicablePlugin: Boolean
}