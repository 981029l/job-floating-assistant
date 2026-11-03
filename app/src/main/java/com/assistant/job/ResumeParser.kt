package com.assistant.job

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream

object ResumeParser {

    fun parseResumeUri(context: Context, uri: Uri): Result<String> {
        return try {
            val fileName = getFileName(context, uri).lowercase()
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法读取选中的文件流"))

            val text = when {
                fileName.endsWith(".pdf") -> parsePdf(context, inputStream)
                fileName.endsWith(".docx") -> parseDocx(inputStream)
                fileName.endsWith(".doc") -> parsePlain(inputStream) // 旧版 doc 尽量当文本/兜底
                else -> parsePlain(inputStream)
            }

            if (text.isBlank()) {
                Result.failure(Exception("未能从文件中解析出有效文字，请检查文件格式"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePdf(context: Context, inputStream: InputStream): String {
        return try {
            PDFBoxResourceLoader.init(context)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document).trim()
            document.close()
            text
        } catch (e: Exception) {
            throw Exception("PDF 解析失败: ${e.message}")
        }
    }

    private fun parseDocx(inputStream: InputStream): String {
        return try {
            val zip = ZipInputStream(inputStream)
            val sb = StringBuilder()
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val content = zip.bufferedReader(Charsets.UTF_8).readText()
                    // 匹配 <w:t> 或者 <w:t ...> 里的文本内容
                    val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                    regex.findAll(content).forEach { match ->
                        sb.append(match.groupValues[1])
                    }
                    break
                }
                entry = zip.nextEntry
            }
            zip.close()
            sb.toString().trim()
        } catch (e: Exception) {
            throw Exception("Word(.docx) 解析失败: ${e.message}")
        }
    }

    private fun parsePlain(inputStream: InputStream): String {
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result = ""
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index) ?: ""
                    }
                }
            }
        }
        if (result.isEmpty()) {
            result = uri.path?.substringAfterLast('/') ?: "resume"
        }
        return result
    }
}
