package com.assistant.job

internal object JobContext {
    data class Identity(val title: String = "", val company: String = "")

    private val jobWords = Regex("工程师|开发|前端|后端|架构|设计|产品|算法|测试|Java|Android|iOS|专家|总监|经理|主管|专员|实习", RegexOption.IGNORE_CASE)
    private val companyWords = Regex("公司|科技|网络|信息|集团|工作室|互娱|企业|银行")
    private val sectionWords = Regex("^(?:[一二三四五六七八九十0-9]+[、.．：:]\\s*)?(职位描述|岗位描述|岗位职责|工作职责|职位职责|任职要求|任职资格|职位要求|岗位要求|招聘要求|工作内容)", RegexOption.MULTILINE)
    private val ignoredHeaders = setOf("公司介绍", "公司信息", "公司主页", "企业介绍", "企业信息", "职位详情", "岗位详情", "职位描述", "岗位职责", "任职要求", "工作职责")
    private val trailingSections = setOf("公司介绍", "公司信息", "工商信息", "相似职位", "推荐职位", "其他职位", "猜你喜欢")

    fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), "").lowercase()

    fun identity(lines: List<String>): Identity {
        val header = lines.takeWhile { !sectionWords.containsMatchIn(it.trim()) }.take(15)
            .map { it.trim() }.filter { it !in ignoredHeaders && !it.contains('：') && !it.contains(':') }
        val title = header.firstOrNull { it.length in 3..40 && jobWords.containsMatchIn(it) } ?: ""
        val company = header.firstOrNull { it != title && it.length in 3..40 && companyWords.containsMatchIn(it) } ?: ""
        return Identity(title, company)
    }

    fun hasJdSection(lines: List<String>): Boolean = lines.any { sectionWords.containsMatchIn(it.trim()) }

    fun jdLines(lines: List<String>): List<String> = lines.takeWhile { it.trim().trimEnd('：', ':') !in trailingSections }

    fun changed(previous: Identity, current: Identity): Boolean =
        (previous.title.isNotBlank() && current.title.isNotBlank() && normalize(previous.title) != normalize(current.title)) ||
            (previous.company.isNotBlank() && current.company.isNotBlank() && normalize(previous.company) != normalize(current.company))
}

internal class JobMemoryStore {
    data class Item(val packageName: String, val identity: JobContext.Identity, val fullJd: String)

    private val items = mutableListOf<Item>()

    fun record(packageName: String, lines: List<String>, fullJd: String) {
        val identity = JobContext.identity(lines)
        if (packageName.isBlank() || identity.title.isBlank() || identity.company.isBlank() ||
            !JobContext.hasJdSection(lines) || fullJd.length < 20) return
        items.removeAll {
            it.packageName == packageName &&
                JobContext.normalize(it.identity.title) == JobContext.normalize(identity.title) &&
                JobContext.normalize(it.identity.company) == JobContext.normalize(identity.company)
        }
        items.add(0, Item(packageName, identity, fullJd))
        if (items.size > 50) items.removeAt(items.lastIndex)
    }

    fun find(packageName: String, chatLines: List<String>): Item? {
        val header = chatLines.take(15).map(JobContext::normalize).filter { it.isNotBlank() }.toSet()
        return items.singleOrNull {
            it.packageName == packageName && JobContext.normalize(it.identity.title) in header &&
                JobContext.normalize(it.identity.company) in header
        }
    }
}
