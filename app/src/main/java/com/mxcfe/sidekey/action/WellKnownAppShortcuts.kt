package com.mxcfe.sidekey.action

import android.content.Intent
import android.net.Uri

/**
 * Deep links for common Chinese apps. Desktop long-press menus often expose these
 * even when ShortcutManager dumps are empty or inaccessible to non-launcher apps.
 */
object WellKnownAppShortcuts {
    fun forPackage(packageName: String): List<AppShortcut> {
        return catalog[packageName].orEmpty().map { item ->
            AppShortcut(
                packageName = packageName,
                id = item.id,
                label = item.label,
                intents = listOf(item.intent(packageName)),
            )
        }
    }

    private data class Item(
        val id: String,
        val label: String,
        val uri: String? = null,
        val action: String = Intent.ACTION_VIEW,
        val component: String? = null,
        val stringExtras: Map<String, String> = emptyMap(),
        val boolExtras: Map<String, Boolean> = emptyMap(),
    ) {
        fun intent(packageName: String): Intent {
            val intent = Intent(action)
            if (!uri.isNullOrBlank()) {
                intent.data = Uri.parse(uri)
            }
            if (!component.isNullOrBlank()) {
                val cls = if (component.startsWith('.')) packageName + component else component
                intent.setClassName(packageName, cls)
            } else {
                intent.setPackage(packageName)
            }
            stringExtras.forEach { (key, value) -> intent.putExtra(key, value) }
            boolExtras.forEach { (key, value) -> intent.putExtra(key, value) }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }

    private val catalog: Map<String, List<Item>> = mapOf(
        "com.tencent.mobileqq" to listOf(
            Item("wk_scan", "扫一扫", uri = "mqqapi://qrcode/scan_qrcode?version=1&src_type=app"),
        ),
        "com.tencent.tim" to listOf(
            Item("wk_scan", "扫一扫", uri = "tim://qrcode/scan_qrcode?version=1&src_type=app"),
        ),
        "com.tencent.mm" to listOf(
            Item(
                id = "wk_scan",
                label = "扫一扫",
                action = Intent.ACTION_MAIN,
                component = "com.tencent.mm.ui.LauncherUI",
                boolExtras = mapOf("LauncherUI.From.Scaner.Shortcut" to true),
            ),
            Item("wk_scan_uri", "扫一扫（协议）", uri = "weixin://scanqrcode"),
        ),
        "com.eg.android.AlipayGphone" to listOf(
            Item("wk_scan", "扫一扫", uri = "alipayqr://platformapi/startapp?saId=10000007"),
            Item("wk_pay", "付款码", uri = "alipayqr://platformapi/startapp?saId=20000056"),
        ),
        "com.taobao.taobao" to listOf(
            Item("wk_scan", "扫一扫", uri = "taobao://tb.cn/n/scancode"),
        ),
        "com.jingdong.app.mall" to listOf(
            Item(
                "wk_scan",
                "扫啊扫",
                uri = "openapp.jdmobile://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22saoasao%22%7D",
            ),
        ),
        "com.ss.android.ugc.aweme" to listOf(
            Item("wk_scan", "扫一扫", uri = "snssdk1128://scan"),
        ),
        "com.sina.weibo" to listOf(
            Item("wk_scan", "扫一扫", uri = "sinaweibo://qrcode"),
        ),
        "com.alibaba.android.rimet" to listOf(
            Item("wk_scan", "扫一扫", uri = "dingtalk://dingtalkclient/page/scan"),
        ),
        "com.xingin.xhs" to listOf(
            Item("wk_scan", "扫一扫", uri = "xhsdiscover://scan"),
        ),
    )
}
