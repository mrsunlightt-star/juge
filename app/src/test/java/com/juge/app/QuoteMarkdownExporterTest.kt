package com.juge.app

import android.net.Uri
import com.juge.app.data.Category
import com.juge.app.data.QuoteMarkdownExporter
import com.juge.app.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuoteMarkdownExporterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `按分类和现有顺序汇总全部金句`() {
        val categories = listOf(
            Category(id = 10, name = "文学"),
            Category(id = 20, name = "空分类"),
        )
        val reminders = listOf(
            Reminder(id = 1, content = "第一句", categoryId = 10),
            Reminder(id = 2, content = "多行第一行\n多行第二行", categoryId = 10),
            Reminder(id = 3, content = "分类已不存在", categoryId = 99),
        )

        val markdown = QuoteMarkdownExporter.buildMarkdown(categories, reminders)

        assertEquals(
            """
            # 句阁 · 金句合集

            共 3 句，按分类汇总。

            ## 文学

            > 第一句

            > 多行第一行
            > 多行第二行

            ## 未分类

            > 分类已不存在

            """.trimIndent(),
            markdown,
        )
    }

    @Test
    fun `没有金句时也产出可打开的 Markdown`() {
        val markdown = QuoteMarkdownExporter.buildMarkdown(
            categories = listOf(Category(id = 1, name = "文学")),
            reminders = listOf(Reminder(id = 1, content = "   ", categoryId = 1)),
        )

        assertEquals(
            """
            # 句阁 · 金句合集

            共 0 句，按分类汇总。

            暂无金句。

            """.trimIndent(),
            markdown,
        )
    }

    @Test
    fun `按 UTF-8 写入用户选择的文件`() {
        val file = temporaryFolder.newFile("句阁金句合集.md")
        val content = "# 句阁 · 金句合集\n\n> 海内存知己，天涯若比邻。\n"

        QuoteMarkdownExporter.write(
            context = RuntimeEnvironment.getApplication(),
            uri = Uri.fromFile(file),
            markdown = content,
        )

        assertEquals(content, file.readText(Charsets.UTF_8))
    }
}
