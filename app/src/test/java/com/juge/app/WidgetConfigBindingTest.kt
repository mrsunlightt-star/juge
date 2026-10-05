package com.juge.app

import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetConfigBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 组件配置行的绑定/回收规则。
 *
 * 这两条规则直接决定「配置表会不会无限增长」和「用户内容会不会被误删」，
 * 属于必须锁住的领域规则，因此从 Provider 里抽出来做纯 JVM 测试。
 */
class WidgetConfigBindingTest {

    private fun auto(id: Long) = WidgetConfig(id = id, name = "微件 $id", content = "c", styleJson = "{}")

    private fun userCreated(id: Long) = WidgetConfig(id = id, name = "导入卡片 - 三体", content = "c", styleJson = "{}")

    @Test
    fun `孤儿只包含自动创建且无人绑定的行`() {
        val configs = listOf(auto(1), auto(2), auto(3), userCreated(4), userCreated(5))

        assertEquals(listOf(2L, 3L), WidgetConfigBinding.orphanIds(configs, setOf(1L, 4L)))
    }

    @Test
    fun `用户手动创建的配置即使无人绑定也不算孤儿`() {
        // 关键：回收只能碰自动建的行，否则会连用户内容一起删掉
        assertEquals(emptyList<Long>(), WidgetConfigBinding.orphanIds(listOf(userCreated(9)), emptySet()))
    }

    @Test
    fun `有组件在用的自动配置不会被回收`() {
        assertEquals(emptyList<Long>(), WidgetConfigBinding.orphanIds(listOf(auto(7)), setOf(7L)))
    }

    @Test
    fun `复用时只挑自动创建且无人绑定的行`() {
        val configs = listOf(userCreated(1), auto(2), auto(3))

        assertEquals(2L, WidgetConfigBinding.findReusableAutoConfig(configs, setOf(9L))?.id)
    }

    @Test
    fun `没有可复用的自动配置时返回 null`() {
        // 已被绑定 -> 不可复用；用户创建的行 -> 不可复用
        assertNull(WidgetConfigBinding.findReusableAutoConfig(listOf(auto(1), userCreated(2)), setOf(1L)))
    }

    @Test
    fun `空配置表不会崩`() {
        assertEquals(emptyList<Long>(), WidgetConfigBinding.orphanIds(emptyList(), emptySet()))
        assertNull(WidgetConfigBinding.findReusableAutoConfig(emptyList(), emptySet()))
    }
}
