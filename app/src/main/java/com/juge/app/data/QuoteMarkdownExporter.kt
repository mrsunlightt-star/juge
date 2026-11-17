package com.juge.app.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * 把文库里的全部金句汇总为 Markdown。
 *
 * 只读取 [Reminder] / [Category]，不包含桌面组件配置或样式；那些是展示设置，
 * 不属于用户想带走的金句正文。分类顺序沿用文库，组内顺序沿用用户手动排序。
 */
internal object QuoteMarkdownExporter {

    const val MIME_TYPE = "text/markdown"
    const val SUGGESTED_FILE_NAME = "句阁金句合集.md"

    fun buildMarkdown(categories: List<Category>, reminders: List<Reminder>): String {
        val quotes = reminders.filter { it.content.isNotBlank() }
        return buildString {
            appendLine("# 句阁 · 金句合集")
            appendLine()
            appendLine("共 ${quotes.size} 句，按分类汇总。")

            if (quotes.isEmpty()) {
                appendLine()
                appendLine("暂无金句。")
                return@buildString
            }

            val byCategory = quotes.groupBy { it.categoryId }
            val orderedCategories = categories.distinctBy { it.id }
            val knownCategoryIds = orderedCategories.mapTo(mutableSetOf()) { it.id }

            orderedCategories.forEach { category ->
                appendQuoteSection(
                    title = category.name,
                    quotes = byCategory[category.id].orEmpty(),
                )
            }
            appendQuoteSection(
                title = "未分类",
                quotes = quotes.filter { it.categoryId !in knownCategoryIds },
            )
        }
    }

    /** 由系统文件保存面板指定落点，按 UTF-8 覆盖写入用户选中的文件。 */
    fun write(context: Context, uri: Uri, markdown: String) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法打开所选文件")
        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
            writer.write(markdown)
        }
    }

    private fun StringBuilder.appendQuoteSection(title: String, quotes: List<Reminder>) {
        if (quotes.isEmpty()) return
        appendLine()
        appendLine("## $title")
        quotes.forEach { reminder ->
            appendLine()
            appendLine(toMarkdownQuote(reminder.content))
        }
    }

    private fun toMarkdownQuote(content: String): String {
        val normalized = content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        return normalized.lineSequence().joinToString("\n") { line ->
            if (line.isEmpty()) ">" else "> $line"
        }
    }
}
