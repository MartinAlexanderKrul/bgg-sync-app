package cz.nicolsburg.boardflow.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

object QrGenerator {

    private const val QR_SIZE   = 300
    private const val SUBFOLDER = "BoardgameSync"

    fun safeName(gameName: String) = gameName.replace(Regex("[\\\\/:*?\"<>|.']+"), "").trim()
    fun fileName(gameName: String) = "${safeName(gameName)} [QR].png"

    fun generatePng(url: String, gameName: String = ""): ByteArray {
        val hints  = mapOf(EncodeHintType.MARGIN to 1,
                           EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H)
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
        val bmp    = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        for (x in 0 until QR_SIZE) for (y in 0 until QR_SIZE) if (matrix[x, y]) bmp.setPixel(x, y, Color.BLACK)
        if (gameName.isNotBlank()) {
            val maxW = QR_SIZE * 0.55f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 26f }
            var line1 = gameName; var line2 = ""
            if (paint.measureText(gameName) > maxW) {
                val words = gameName.split(" "); val split = words.size / 2
                line1 = words.take(split).joinToString(" "); line2 = words.drop(split).joinToString(" ")
                val longer = if (paint.measureText(line1) >= paint.measureText(line2)) line1 else line2
                while (paint.measureText(longer) > maxW && paint.textSize > 12f) paint.textSize -= 1f
            }
            val fm = paint.fontMetrics; val lineH = fm.descent - fm.ascent
            val twoLines = line2.isNotEmpty()
            val textH = if (twoLines) lineH * 2 + 2f else lineH
            val textW = if (twoLines) maxOf(paint.measureText(line1), paint.measureText(line2)) else paint.measureText(line1)
            val padX = 4f; val padY = 3f; val boxW = textW + padX * 2; val boxH = textH + padY * 2
            val boxX = (QR_SIZE - boxW) / 2f; val boxY = (QR_SIZE - boxH) / 2f
            canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(180, 180, 180); style = Paint.Style.STROKE; strokeWidth = 1f })
            if (twoLines) {
                canvas.drawText(line1, boxX + padX + (textW - paint.measureText(line1)) / 2f, boxY + padY - fm.ascent, paint)
                canvas.drawText(line2, boxX + padX + (textW - paint.measureText(line2)) / 2f, boxY + padY - fm.ascent + lineH + 2f, paint)
            } else {
                canvas.drawText(line1, boxX + padX, boxY + padY - fm.ascent, paint)
            }
        }
        val baos = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    fun isInGallery(context: Context, fileName: String): Boolean {
        val relPath = "${Environment.DIRECTORY_PICTURES}/$SUBFOLDER"
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, arrayOf(fileName, "$relPath/"), null)?.use { return it.moveToFirst() }
        return false
    }

    fun saveToGallery(context: Context, gameName: String, pngBytes: ByteArray): Boolean {
        val fileName = fileName(gameName); val relPath = "${Environment.DIRECTORY_PICTURES}/$SUBFOLDER"
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        resolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(fileName, "$relPath/"), null)?.use { if (it.moveToFirst()) return false }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "$relPath/")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(pngBytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }
}
