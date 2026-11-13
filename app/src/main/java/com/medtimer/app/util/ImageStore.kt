package com.medtimer.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 药盒照片存储：主图长边 1080px、缩略图长边 240px，JPEG 质量 80，落在应用私有目录。
 * 解码先用 inSampleSize 粗采样再精确缩放，避免大图 OOM。
 */
object ImageStore {

    private const val DIR_NAME = "images"
    private const val IMAGE_LONG_SIDE = 1080
    private const val THUMB_LONG_SIDE = 240
    private const val JPEG_QUALITY = 80

    /** 保存选中的图片，返回 (主图路径, 缩略图路径)。 */
    suspend fun save(context: Context, uri: Uri): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Cannot create image directory: ${dir.absolutePath}")
            }
            val id = UUID.randomUUID().toString()
            val imageFile = File(dir, "med_$id.jpg")
            val thumbFile = File(dir, "med_${id}_thumb.jpg")

            val decoded = decodeSampled(context, uri, IMAGE_LONG_SIDE)
                ?: throw IOException("Cannot decode image: $uri")
            val image = scaleToLongSide(decoded, IMAGE_LONG_SIDE)
            try {
                writeJpeg(image, imageFile)
            } finally {
                if (image !== decoded) image.recycle()
                decoded.recycle()
            }

            val thumbSource = decodeSampled(context, uri, THUMB_LONG_SIDE)
                ?: throw IOException("Cannot decode thumbnail: $uri")
            val thumb = scaleToLongSide(thumbSource, THUMB_LONG_SIDE)
            try {
                writeJpeg(thumb, thumbFile)
            } finally {
                if (thumb !== thumbSource) thumb.recycle()
                thumbSource.recycle()
            }

            imageFile.absolutePath to thumbFile.absolutePath
        }

    /** 删除主图与缩略图文件；文件不存在时静默忽略。 */
    fun delete(imagePath: String?, thumbPath: String?) {
        for (path in listOfNotNull(imagePath, thumbPath)) {
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
    }

    /** 列表用的缩略图解码，失败返回 null。 */
    fun loadThumbnail(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { decodeSampledFile(file, THUMB_LONG_SIDE) }.getOrNull()
    }

    /** 网格/预览用的主图解码，失败返回 null。 */
    fun loadImage(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { decodeSampledFile(file, IMAGE_LONG_SIDE) }.getOrNull()
    }

    private fun decodeSampled(context: Context, uri: Uri, targetLongSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream { resolver.openInputStream(uri) }?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetLongSide)
        }
        return openStream { resolver.openInputStream(uri) }
            ?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeSampledFile(file: File, targetLongSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetLongSide)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /** 让采样后长边仍不小于目标值，再交给精确缩放。 */
    private fun sampleSizeFor(width: Int, height: Int, targetLongSide: Int): Int {
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= targetLongSide) {
            sample *= 2
        }
        return sample
    }

    private fun scaleToLongSide(source: Bitmap, targetLongSide: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= targetLongSide) return source
        val ratio = targetLongSide.toFloat() / longest
        val width = max(1, (source.width * ratio).roundToInt())
        val height = max(1, (source.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                throw IOException("Cannot write image: ${file.absolutePath}")
            }
        }
    }

    /** 用 lambda 打开两次流，避免复用已关闭的 InputStream。 */
    private inline fun openStream(open: () -> InputStream?): InputStream? =
        runCatching(open).getOrNull()
}