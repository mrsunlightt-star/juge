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

    private fun auto(id: Long) = WidgetConfig(id = id, name = "微件 $id", content = "c", styleJson = "{}", autoCreated = true)

    private fun userCreated(id: Long) = WidgetConfig(id = id, name = "导入卡片 - 三体", content = "c", styleJson = "{}")

    @Test
    fun `孤儿只包含自动创建且无人绑定的行`() {
        val configs = listOf(auto(1), auto(2), auto(3), userCreated(4), userCreated(5))

        assertEquals(listOf(2L, 3L), WidgetConfigBinding.orphanIds(configs, setOf(1L, 4L)))
    }

    @Test
    fun `回收只认 auto_created 列，不再看名称前缀`() {
        // 旧版只看「微件 」前缀，用户把卡片命名成"微件 开头"会被误回收删内容；
        // 现在归类只读列，名字与归类彻底解耦
        val falselyPrefixed = listOf(
            WidgetConfig(id = 1, name = "微件 测试", content = "c", styleJson = "{}"),
        )
        assertEquals(emptyList<Long>(), WidgetConfigBinding.orphanIds(falselyPrefixed, emptySet()))

        val autoWithoutPrefix = listOf(
            WidgetConfig(id = 2, name = "随便什么名字", content = "c", styleJson = "{}", autoCreated = true),
        )
        assertEquals(listOf(2L), WidgetConfigBinding.orphanIds(autoWithoutPrefix, emptySet()))
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
    fun `用户前缀命名的行不会被复用覆盖`() {
        // 「把金句推到桌面」复用空自动行：只允许塞进 autoCreated 行，
        // 用户的「微件 xx」绝不能被改写内容
        val rows = listOf(WidgetConfig(id = 3, name = "微件 自建", content = "c", styleJson = "{}"))

        assertNull(WidgetConfigBinding.findReusableAutoConfig(rows, emptySet()))
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

    @Test
    fun `恢复交换ID时从原始快照读取绑定和样式`() {
        val bindings = mapOf(
            10 to WidgetConfigBinding.Binding(1L, "style-a"),
            20 to WidgetConfigBinding.Binding(2L, "style-b")
        )
        assertEquals(
            mapOf(20 to bindings.getValue(10), 10 to bindings.getValue(20)),
            WidgetConfigBinding.remap(bindings, intArrayOf(10, 20), intArrayOf(20, 10))
        )
        assertEquals(1L, bindings.getValue(10).configId)
    }

    @Test
    fun `跨Provider恢复不删除已经成为新ID的旧ID`() {
        val source = mapOf(
            10 to WidgetConfigBinding.Binding(1L, "style-a"),
            20 to WidgetConfigBinding.Binding(2L, "style-b")
        )
        val first = WidgetConfigBinding.remap(source, intArrayOf(10), intArrayOf(20))
        val second = WidgetConfigBinding.remap(first, intArrayOf(20), intArrayOf(30), source, setOf(20))
        assertEquals(mapOf(20 to source.getValue(10), 30 to source.getValue(20)), second)
    }

    @Test
    fun `恢复来源缺失时不会继承目标碰撞ID的样式`() {
        val bindings = mapOf(20 to WidgetConfigBinding.Binding(2L, "unrelated"))
        assertEquals(
            mapOf(30 to bindings.getValue(20)),
            WidgetConfigBinding.remap(bindings, intArrayOf(10, 20), intArrayOf(20, 30))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `重复目标ID不会静默覆盖恢复数据`() {
        WidgetConfigBinding.remap(emptyMap(), intArrayOf(10, 20), intArrayOf(30, 30))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `长度不一致的恢复映射被拒绝`() {
        WidgetConfigBinding.remap(emptyMap(), intArrayOf(10), intArrayOf(20, 30))
    }
}
