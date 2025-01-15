package com.yh.module_crawler.utils

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

/**
 * bili视频爬取
 */

private const val path = "https://search.bilibili.com/video"
private val result: MutableSet<VideoEntity> = HashSet()

fun main(args: Array<String>) = runBlocking {
    if (args.size == 1 && args[0] == "-h") {
        println("{keyword} [pageNum]")
        exitProcess(0)
    }
    if (args.size < 2) {
        println("参数数量错误")
        exitProcess(-1)
    }
    try {
        crawler(args[0], args[1].toInt())
    } catch (e: NumberFormatException) {
        e.printStackTrace()
    }
}

private suspend fun crawler(keyWord: String, pageNum: Int = 1) {
    flow {
        repeat(pageNum) { i ->
            println("正在采集第 ${i + 1} 页")
            getDoc(keyWord, i)?.select("div[class^='video-list-item']")?.forEach { element ->
                getEntity(element)?.let { emit(it) }
            }
        }
    }.collect { result.add(it) }
    save(keyWord)
}

private fun getDoc(keyWord: String, page: Int): Document? {
    var url = "$path?keyword=$keyWord"
    if (page != 1) url += "&page=$page&o=24"
    return try {
        Jsoup.connect(url).get()
    } catch (e: Exception) {
        println("网站失效 ${e.message}")
        null
    }
}

private suspend fun save(keyWord: String) {
    val json = Gson().toJson(result)
//    println(json)
    val outFile = File("$keyWord.json")
    withContext(Dispatchers.IO) {
        FileOutputStream(outFile).use {
            it.write(json.toByteArray())
        }
    }
    println("\n导出数据完成，路径为: ${outFile.absolutePath}")
}

private fun getEntity(element: Element): VideoEntity? {
    val aTag = element.childNode(0).childNode(1).childNode(1)
    if (aTag.attr("href").startsWith("https")) return null
    val imgTag = aTag.childNode(0).childNode(0).childNode(1).childNode(3)
    return VideoEntity(
        imgTag.attr("alt"),
        "https:" + imgTag.attr("src").split("@").first(),
        aTag.attr("href").split("/")[4],
        imgTag.attr("alt"),
        element.selectFirst("span[class='bili-video-card__stats__duration']")?.text() ?: "",
        element.selectFirst("span[class='bili-video-card__info--author']")?.text() ?: ""
    )
}

data class VideoEntity(
    val title: String,
    val cover: String,
    val vid: String,
    val intro: String,
    val timestamp: String,
    val author: String,
    val number: String = "1",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return vid == (other as VideoEntity).vid
    }

    override fun hashCode(): Int {
        return vid.hashCode()
    }
}