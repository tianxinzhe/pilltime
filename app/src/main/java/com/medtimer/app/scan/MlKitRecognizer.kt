package com.medtimer.app.scan

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * ML Kit 端侧识别封装：文字识别与条码扫描都使用 bundled 模型，
 * 图片只在内存与本机模型间流转，识别全程离线，不会发起网络请求。
 * 所有异常都被吞掉并返回空结果，识别不可用时不影响手工录入。
 */
class MlKitRecognizer {

    /**
     * 仅做类加载检查，不初始化模型；任何异常都视为不可用，
     * 调用方据此隐藏识别入口。
     */
    fun isAvailable(context: Context): Boolean = runCatching {
        val loader = context.classLoader
        Class.forName("com.google.mlkit.vision.text.TextRecognition", false, loader)
        Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions", false, loader)
        Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning", false, loader)
        Class.forName("com.google.mlkit.vision.barcode.common.Barcode", false, loader)
    }.isSuccess

    /** 返回识别出的按行文本；失败或没有结果时返回空列表。 */
    suspend fun recognizeText(context: Context, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    client.process(image).awaitResult()
                } finally {
                    client.close()
                }
            }.getOrNull()
                ?.textBlocks
                ?.flatMap { block -> block.lines.map { line -> line.text } }
                .orEmpty()
        }

    /** 返回首个条码值；失败或没有条码时返回 null。 */
    suspend fun recognizeBarcode(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val client = BarcodeScanning.getClient()
                try {
                    client.process(image).awaitResult()
                } finally {
                    client.close()
                }
            }.getOrNull()
                ?.firstOrNull()
                ?.rawValue
        }

    /** 用协程桥接 GMS Task，避免额外引入 kotlinx-coroutines-play-services。 */
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}