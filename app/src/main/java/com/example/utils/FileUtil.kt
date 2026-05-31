package com.example.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object FileUtil {
    fun copyUriToLocalAndCalculateMd5(context: Context, uri: Uri): Pair<String, String>? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null
            
            val digest = MessageDigest.getInstance("MD5")
            val targetFile = File(context.filesDir, "ocr_${UUID.randomUUID()}.jpg")
            val fos = FileOutputStream(targetFile)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
                digest.update(buffer, 0, bytesRead)
            }
            
            fos.close()
            inputStream.close()
            
            val md5Bytes = digest.digest()
            val md5String = md5Bytes.joinToString("") { "%02x".format(it) }
            
            Pair(targetFile.absolutePath, md5String)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun calculateMd5OfFile(fileFile: File): String? {
        return try {
             val digest = MessageDigest.getInstance("MD5")
             val inputStream = fileFile.inputStream()
             val buffer = ByteArray(8192)
             var bytesRead: Int
             while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
             }
             inputStream.close()
             val md5Bytes = digest.digest()
             md5Bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
