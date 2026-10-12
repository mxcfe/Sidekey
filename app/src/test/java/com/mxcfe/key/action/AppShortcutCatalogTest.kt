package com.mxcfe.key.action

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShortcutCatalogTest {
    @Test
    fun parseIndentedDump() {
        val dump = """
            com.tencent.mobileqq:
              scan_qr:
                ShortLabel: 扫一扫
                LongLabel: 打开扫一扫
              create_chat:
                ShortLabel: 创建聊天
        """.trimIndent()
        val list = AppShortcutCatalog.parseShellShortcutOutput(dump, "com.tencent.mobileqq")
        assertEquals(2, list.size)
        assertEquals("扫一扫", list.first { it.id == "scan_qr" }.label)
        assertEquals("创建聊天", list.first { it.id == "create_chat" }.label)
    }
}
