package com.assistant.job

import org.json.JSONArray
import org.json.JSONObject

internal object JobContext {
    data class Identity(val title: String = "", val company: String = "")

    private val jobWords = Regex("工程师|开发|研发|全栈|前端|后端|架构|设计|产品|算法|测试|运维|数据|Java|Android|iOS|Web|专家|总监|经理|主管|专员|实习", RegexOption.IGNORE_CASE)
    private val companyWords = Regex("公司|科技|网络|信息|集团|工作室|互娱|企业|银行|软件|数据|智能|电商|传媒|文化|咨询|教育|医疗|生物|半导体|电子|通信|金融|证券|保险|汽车|能源|物流|贸易|商贸|实业|研究院|实验室")
    private val sectionWords = Regex("^(?:[一二三四五六七八九十0-9]+[、.．：:]\\s*)?(职位描述|岗位描述|岗位职责|工作职责|职位职责|任职要求|任职资格|职位要求|岗位要求|招聘要求|工作内容|职位详情|岗位详情|职位介绍|岗位介绍|职责描述|工作要求)", RegexOption.MULTILINE)
    private val ignoredHeaders = setOf("公司介绍", "公司信息", "公司主页", "企业介绍", "企业信息", "职位详情", "岗位详情", "职位描述", "岗位职责", "任职要求", "工作职责")
    private val companyCardHeaders = setOf("公司信息", "公司介绍", "企业信息", "企业介绍")
    private val trailingSections = setOf("公司介绍", "公司信息", "工商信息", "相似职位", "推荐职位", "其他职位", "猜你喜欢")
    private val hrBadgePattern = Regex(
        "^([^·•・|｜\\s]{2,24})\\s*[·•・|｜]\\s*(?:HR|HRBP|招聘|人事|面试官|技术|前端|后端|全栈|研发|产品|项目|部门|团队|老板|创始人|合伙人|CEO|CTO|VP|总监|经理|主管|负责人|架构师|专家|组长|Leader|Recruiter).*$",
        RegexOption.IGNORE_CASE
    )
    private val titlePrefixPattern = Regex("^(?:沟通职位|沟通的职位|沟通过的职位|应聘职位|职位|岗位)[：:]\\s*")
    private val titleSalarySuffixPattern = Regex("\\s*(?:\\d{1,3}(?:\\.\\d+)?\\s*[-~—至]\\s*\\d{1,3}(?:\\.\\d+)?\\s*[kK万千](?:\\s*[·•xX×*]\\s*\\d{1,2}薪)?|面议|薪资面议)$")
    private val cityParenPattern = Regex("[（(][^）()]{2,8}[）)]")
    private val legalSuffixPattern = Regex("(?:股份有限公司|有限责任公司|有限公司)$")

    fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), "").lowercase()

    fun cleanTitle(text: String): String = text.trim()
        .replace(titlePrefixPattern, "")
        .replace(titleSalarySuffixPattern, "")
        .trim()

    fun extractCompanyFromHrBadge(text: String): String? {
        val match = hrBadgePattern.matchEntire(text.trim()) ?: return null
        val company = match.groupValues[1].trim()
        return company.takeIf { it.length in 2..24 && it !in ignoredHeaders }
    }

    fun coreCompanyName(text: String): String {
        val normalized = normalize(text)
        if (normalized.isBlank()) return ""
        val stripped = normalized
            .replace(cityParenPattern, "")
            .replace(legalSuffixPattern, "")
        return if (stripped.length >= 2) stripped else normalized
    }

    private fun companyFromCompanyCard(lines: List<String>): String {
        for (index in lines.indices) {
            val trimmed = lines[index].trim().trimEnd('：', ':')
            if (trimmed in companyCardHeaders) {
                val candidates = lines.drop(index + 1).take(4)
                for (raw in candidates) {
                    val line = raw.trim()
                    if (line.trimEnd('：', ':') in trailingSections) break
                    if (line.length in 2..40 &&
                        line !in ignoredHeaders &&
                        !line.contains('：') && !line.contains(':') &&
                        !line.contains('。') && !line.contains('，') &&
                        companyWords.containsMatchIn(line)
                    ) {
                        return line
                    }
                }
            }
        }
        return ""
    }

    fun identity(lines: List<String>): Identity {
        val header = lines.takeWhile { !sectionWords.containsMatchIn(it.trim()) }.take(20)
            .map { it.trim() }.filter { it.isNotBlank() && it !in ignoredHeaders && !it.contains('：') && !it.contains(':') }
        val title = header.firstOrNull { line ->
            extractCompanyFromHrBadge(line) == null &&
                cleanTitle(line).length in 3..40 &&
                jobWords.containsMatchIn(cleanTitle(line))
        }?.let(::cleanTitle) ?: ""
        val directCompany = header.firstOrNull { line ->
            cleanTitle(line) != title &&
                extractCompanyFromHrBadge(line) == null &&
                line.length in 3..40 &&
                companyWords.containsMatchIn(line)
        }
        val company = directCompany
            ?: companyFromCompanyCard(lines).takeIf { it.isNotBlank() }
            ?: header.firstNotNullOfOrNull(::extractCompanyFromHrBadge)
            ?: ""
        return Identity(title, company)
    }

    fun hasJdSection(lines: List<String>): Boolean = lines.any { sectionWords.containsMatchIn(it.trim()) }

    fun jdLines(lines: List<String>): List<String> = lines.takeWhile { it.trim().trimEnd('：', ':') !in trailingSections }

    fun sameCompany(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        val ca = coreCompanyName(a)
        val cb = coreCompanyName(b)
        if (ca == cb && ca.length >= 2) return true
        val shorter = if (ca.length <= cb.length) ca else cb
        val longer = if (ca.length <= cb.length) cb else ca
        return shorter.length >= 2 && longer.startsWith(shorter) && longer.length > shorter.length
    }

    fun isVerifiedCompanyLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length in 3..40 &&
            trimmed !in ignoredHeaders &&
            !trimmed.contains('：') && !trimmed.contains(':') &&
            !trimmed.contains('。') && !trimmed.contains('，') &&
            companyWords.containsMatchIn(trimmed)
    }

    fun changed(previous: Identity, current: Identity): Boolean =
        (previous.title.isNotBlank() && current.title.isNotBlank() && normalize(previous.title) != normalize(current.title)) ||
            (previous.company.isNotBlank() && current.company.isNotBlank() && !sameCompany(previous.company, current.company))
}

internal class JobMemoryStore {
    data class Item(val packageName: String, val identity: JobContext.Identity, val fullJd: String)

    private val items = mutableListOf<Item>()

    fun record(
        packageName: String,
        lines: List<String>,
        fullJd: String,
        resolvedIdentity: JobContext.Identity = JobContext.identity(lines)
    ): Boolean {
        val fallback = JobContext.identity(lines)
        val identity = JobContext.Identity(
            title = resolvedIdentity.title.ifBlank { fallback.title },
            company = resolvedIdentity.company.ifBlank { fallback.company }
        )
        if (packageName.isBlank() || identity.title.isBlank() || identity.company.isBlank() ||
            !JobContext.hasJdSection(lines) || fullJd.length < 20) return false
        val existing = items.firstOrNull {
            it.packageName == packageName &&
                JobContext.normalize(it.identity.title) == JobContext.normalize(identity.title) &&
                JobContext.sameCompany(it.identity.company, identity.company)
        }
        val mergedIdentity = if (existing != null && existing.identity.company.length > identity.company.length) {
            identity.copy(company = existing.identity.company)
        } else {
            identity
        }
        items.removeAll {
            it.packageName == packageName &&
                JobContext.normalize(it.identity.title) == JobContext.normalize(mergedIdentity.title) &&
                JobContext.sameCompany(it.identity.company, mergedIdentity.company)
        }
        items.add(0, Item(packageName, mergedIdentity, fullJd))
        if (items.size > 100) items.removeAt(items.lastIndex)
        return true
    }

    fun find(packageName: String, chatLines: List<String>): Item? {
        val headerLines = chatLines.take(30).map { it.trim() }.filter { it.isNotBlank() }
        val titleTokens = headerLines.map { JobContext.normalize(JobContext.cleanTitle(it)) }
            .filter { it.isNotBlank() }.toSet()
        val exactCompanyTokens = buildSet {
            headerLines.forEach { line ->
                add(JobContext.normalize(line))
                JobContext.extractCompanyFromHrBadge(line)?.let { add(JobContext.normalize(it)) }
            }
        }
        val exactMatch = items.filter {
            it.packageName == packageName &&
                JobContext.normalize(it.identity.title) in titleTokens &&
                JobContext.normalize(it.identity.company) in exactCompanyTokens
        }
        if (exactMatch.isNotEmpty()) return exactMatch.singleOrNull()

        val hrBadgeCompanies = headerLines.mapNotNull(JobContext::extractCompanyFromHrBadge)
        val verifiedCompanyLines = headerLines.filter(JobContext::isVerifiedCompanyLine)
        return items.singleOrNull { item ->
            item.packageName == packageName &&
                JobContext.normalize(item.identity.title) in titleTokens &&
                (hrBadgeCompanies.any { JobContext.sameCompany(item.identity.company, it) } ||
                    verifiedCompanyLines.any { line ->
                        JobContext.sameCompany(item.identity.company, line) &&
                            minOf(
                                JobContext.coreCompanyName(item.identity.company).length,
                                JobContext.coreCompanyName(line).length
                            ) >= 3
                    })
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("packageName", item.packageName)
                put("title", item.identity.title)
                put("company", item.identity.company)
                put("fullJd", item.fullJd)
            })
        }
        return arr.toString()
    }

    fun loadJson(raw: String) {
        if (raw.isBlank()) return
        try {
            val arr = JSONArray(raw)
            val loaded = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pkg = obj.optString("packageName", "").trim()
                val title = obj.optString("title", "").trim()
                val company = obj.optString("company", "").trim()
                val fullJd = obj.optString("fullJd", "").trim()
                if (pkg.isNotBlank() && title.isNotBlank() && company.isNotBlank() && fullJd.length >= 20) {
                    loaded.add(Item(pkg, JobContext.Identity(title, company), fullJd))
                }
            }
            if (loaded.isNotEmpty()) {
                items.clear()
                items.addAll(loaded.take(100))
            }
        } catch (_: Exception) {
        }
    }
}
