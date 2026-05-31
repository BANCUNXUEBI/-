package com.example.ocr.paddle

import com.example.BuildConfig
import com.example.data.OcrStatus
import com.example.ocr.OcrTaskProgress
import com.example.ocr.OcrOptions
import com.example.ocr.OcrProvider
import com.example.ocr.OcrRawResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class PaddleOcrProvider : OcrProvider {

    private val moshi = Moshi.Builder().build()
    private val apiService: PaddleOcrApiService

    private val okHttpClient: OkHttpClient

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://paddleocr.aistudio-app.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        apiService = retrofit.create(PaddleOcrApiService::class.java)
    }

    override suspend fun recognizeImage(
        imageFile: File, 
        options: OcrOptions?,
        onProgress: (suspend (OcrTaskProgress) -> Unit)?
    ): OcrRawResult {
        // IMPORTANT SECURITY NOTE:
        // Injecting the token via BuildConfig for direct client-to-server API calls is only appropriate
        // for isolated internal testing. If deployed to a formal production environment, users can decompile
        // the APK and extract the hardcoded PADDLEOCR_TOKEN, compromising internal quota and security.
        // For production, you MUST proxy these OCR requests through your own backend server.
        val token = options?.apiKey ?: BuildConfig.PADDLEOCR_TOKEN
        if (token.isBlank() || token.startsWith("MY_")) {
            val errMsg = "Please configure PADDLEOCR_TOKEN in Settings or .env"
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.FAILED, errorStage = "TOKEN_MISSING", errorMessage = errMsg))
            return OcrRawResult(
                provider = "PADDLE_OCR",
                model = "PaddleOCR-VL-1.6",
                imagePath = imageFile.absolutePath,
                isSuccess = false,
                errorMsg = errMsg
            )
        }
        
        if (!imageFile.exists() || imageFile.length() == 0L) {
            val errMsg = "File invalid: exists=${imageFile.exists()}, length=${imageFile.length()}"
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.FAILED, errorStage = "IMPORT_FILE_INVALID", errorMessage = errMsg))
            return OcrRawResult(
                provider = "PADDLE_OCR",
                model = "PaddleOCR-VL-1.6",
                imagePath = imageFile.absolutePath,
                isSuccess = false,
                errorMsg = errMsg
            )
        }

        val authHeader = "bearer $token"
        
        val actualOptions = options ?: OcrOptions()
        val payloadConfig = PaddleOcrSubmitRequest(
            useDocOrientationClassify = actualOptions.useDocOrientationClassify,
            useDocUnwarping = actualOptions.useDocUnwarping,
            useChartRecognition = actualOptions.useChartRecognition
        )
        val payloadAdapter = moshi.adapter(PaddleOcrSubmitRequest::class.java)
        val payloadStr = payloadAdapter.toJson(payloadConfig)

        val ext = imageFile.extension.lowercase()
        val mimeType = if (ext == "png") "image/png" else "image/jpeg"
        val fileReqBody = imageFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", imageFile.name, fileReqBody)
        val modelReqBody = "PaddleOCR-VL-1.6".toRequestBody("text/plain".toMediaTypeOrNull())
        val payloadReqBody = payloadStr.toRequestBody("text/plain".toMediaTypeOrNull())

        try {
            val submitRes = apiService.submitJob(authHeader, part, modelReqBody, payloadReqBody)
            val statusCode = submitRes.code()
            val rawBodyStr = submitRes.body()?.string()
            val rawErrorBodyStr = submitRes.errorBody()?.string()
            
            val tokenMasked = if (token.length > 8) "${token.take(6)}******${token.takeLast(4)}" else "***"
            val debugSubmitInfo = """
                HTTP $statusCode
                Response body: $rawBodyStr
                Error body: $rawErrorBodyStr
                Token configured: true
                Token length: ${token.length}
                Token masked: $tokenMasked
                Authorization header present: true
                Model: PaddleOCR-VL-1.6
                File: ${imageFile.name} (exists=${imageFile.exists()}, size=${imageFile.length()})
                MIME: $mimeType
                Payload: $payloadStr
            """.trimIndent()
            
            onProgress?.invoke(OcrTaskProgress(
                status = if (submitRes.isSuccessful) OcrStatus.JOB_SUBMITTED else OcrStatus.FAILED,
                errorStage = if (submitRes.isSuccessful) null else "SUBMIT_JOB",
                rawSubmitResponse = debugSubmitInfo
            ))

            if (!submitRes.isSuccessful) {
                val msg = "Submit failed: HTTP $statusCode, errorBody=$rawErrorBodyStr, body=$rawBodyStr"
                return OcrRawResult(
                    provider = "PADDLE_OCR",
                    model = "PaddleOCR-VL-1.6",
                    imagePath = imageFile.absolutePath,
                    isSuccess = false,
                    errorMsg = msg
                )
            }

            val jobId = try {
                val root = org.json.JSONObject(rawBodyStr ?: "{}")
                val data = root.optJSONObject("data")
                data?.optString("jobId")?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }

            if (jobId == null) {
                return OcrRawResult(
                    provider = "PADDLE_OCR",
                    model = "PaddleOCR-VL-1.6",
                    imagePath = imageFile.absolutePath,
                    isSuccess = false,
                    errorMsg = "Submit failed: HTTP 200 but no jobId found. Raw: $rawBodyStr"
                )
            }
            
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.POLLING, jobId = jobId))
            
            // Poll for up to 120s
            // wait 3s first
            delay(3000)
            
            var jobDone = false
            var finalJsonUrl: String = ""
            var errorMsg: String? = null
            
            val maxRetries = 40 // 40 * 3s = 120s
            var rawPollResponse: String? = null
            for (i in 0 until maxRetries) {
                val statusRes = apiService.getJobStatus(authHeader, jobId)
                if (statusRes.isSuccessful) {
                    val rawBodyStr = statusRes.body()?.string()
                    rawPollResponse = rawBodyStr
                    val root = org.json.JSONObject(rawBodyStr ?: "{}")
                    val data = root.optJSONObject("data")
                    val state = data?.optString("state")
                    if (state == "done") {
                        val resultUrl = data.optJSONObject("resultUrl")
                        val jsonUrl = resultUrl?.optString("jsonUrl")
                        if (!jsonUrl.isNullOrBlank()) {
                            finalJsonUrl = jsonUrl
                            jobDone = true
                        } else {
                            errorMsg = "Task done but no jsonUrl found."
                        }
                        break
                    } else if (state == "failed") {
                        errorMsg = root.optString("error").takeIf { it.isNotBlank() } ?: "Task failed on server"
                        break
                    }
                } else {
                    errorMsg = "Failed to poll status: ${statusRes.code()}"
                    break
                }
                delay(3000)
            }

            if (!jobDone || finalJsonUrl.isEmpty()) {
                val errMsg = errorMsg ?: "Timeout after 120s polling"
                onProgress?.invoke(OcrTaskProgress(status = OcrStatus.FAILED, errorStage = "Polling", errorMessage = errMsg, rawPollResponse = rawPollResponse))
                return OcrRawResult(
                    provider = "PADDLE_OCR",
                    model = "PaddleOCR-VL-1.6",
                    imagePath = imageFile.absolutePath,
                    jobId = jobId,
                    isSuccess = false,
                    errorMsg = errMsg
                )
            }
            
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.OCR_DONE, rawPollResponse = rawPollResponse, jsonUrl = finalJsonUrl))
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.JSONL_DOWNLOADING, jsonUrl = finalJsonUrl))

            // Download JSONL
            val request = okhttp3.Request.Builder()
                .url(finalJsonUrl)
                .get()
                .build()

            val jsonlRes = okHttpClient.newCall(request).execute()
            if (!jsonlRes.isSuccessful || jsonlRes.body == null) {
                val errorBody = jsonlRes.body?.string()
                val errMsg = "Failed to download JSONL: ${jsonlRes.code}"
                onProgress?.invoke(OcrTaskProgress(status = OcrStatus.FAILED, errorStage = "DownloadJsonl", errorMessage = errMsg, jsonlHttpStatus = jsonlRes.code, jsonlBodyLength = errorBody?.length))
                return OcrRawResult(
                    provider = "PADDLE_OCR",
                    model = "PaddleOCR-VL-1.6",
                    imagePath = imageFile.absolutePath,
                    jobId = jobId,
                    isSuccess = false,
                    errorMsg = errMsg
                )
            }

            val jsonlText = jsonlRes.body!!.string()
            
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.JSONL_DOWNLOADED, jsonlHttpStatus = jsonlRes.code, jsonlBodyLength = jsonlText.length))
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.TABLE_EXTRACTING))
            
            // Parse JSONL
            var markdownTextStr = ""
            var layoutJsonStr = "[]"
            var outputImagesJsonStr = "[]"
            
            try {
                val lineAdapter = moshi.adapter(PaddleJsonlLine::class.java)
                val lines = jsonlText.lines()
                if (lines.isNotEmpty()) {
                    val firstLine = lines.firstOrNull { it.isNotBlank() }
                    if (firstLine != null) {
                        val rootObj = org.json.JSONObject(firstLine)
                        val resultObj = rootObj.optJSONObject("result")
                        
                        markdownTextStr = resultObj?.optJSONObject("markdown")?.optString("text") ?: ""
                        
                        val layoutParsingResultsJsonObj = resultObj?.optJSONArray("layoutParsingResults")
                        if (layoutParsingResultsJsonObj != null) {
                            layoutJsonStr = layoutParsingResultsJsonObj.toString()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.LEDGER_PARSING))

            val rawJobJson = "{}"

            return OcrRawResult(
                provider = "PADDLE_OCR",
                model = "PaddleOCR-VL-1.6",
                imagePath = imageFile.absolutePath,
                jobId = jobId,
                rawJobJson = rawJobJson,
                jsonlText = jsonlText,
                markdownText = markdownTextStr,
                layoutParsingResultsJson = layoutJsonStr,
                outputImagesJson = outputImagesJsonStr,
                isSuccess = true
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress?.invoke(OcrTaskProgress(status = OcrStatus.FAILED, errorStage = "Exception", errorMessage = e.message))
            return OcrRawResult(
                provider = "PADDLE_OCR",
                model = "PaddleOCR-VL-1.6",
                imagePath = imageFile.absolutePath,
                isSuccess = false,
                errorMsg = e.message
            )
        }
    }
}
