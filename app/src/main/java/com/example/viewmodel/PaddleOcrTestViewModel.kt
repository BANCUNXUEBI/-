package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.ocr.paddle.*
import com.example.BuildConfig
import com.squareup.moshi.Moshi
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
import kotlinx.coroutines.delay
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PaddleTestUiState(
    val imageFile: File? = null,
    val tokenStatus: String = "未配置",
    val requestStatus: String = "Idle",
    val httpStatusCode: Int? = null,
    val rawSubmitResponse: String? = null,
    val rawSubmitErrorBody: String? = null,
    val modelParamsLog: String? = null,
    val jobId: String? = null,
    val pollUrl: String? = null,
    val pollHttpStatusCode: Int? = null,
    val rawPollResponse: String? = null,
    val rawPollErrorBody: String? = null,
    val pollingStatus: String = "",
    val extractedPages: Int? = null,
    val totalPages: Int? = null,
    val jsonUrl: String? = null,
    val jsonlHttpStatusCode: Int? = null,
    val jsonlErrorBody: String? = null,
    val jsonlDownloadStatus: String? = null,
    val jsonlBodyLength: Int? = null,
    val jsonlParsedTableCount: Int? = null,
    val jsonlFirstTableHtml: String? = null,
    val jsonlLedgerRowsCount: Int? = null,
    val jsonlLedgerRowsPreview: String? = null,
    val jsonlParseErrorTrace: String? = null,
    val markdownText: String? = null,
    val jsonlText: String? = null,
    val layoutParsingResultsJson: String? = null,
    val errorMsg: String? = null
)

class PaddleOcrTestViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PaddleTestUiState())
    val uiState: StateFlow<PaddleTestUiState> = _uiState.asStateFlow()

    private val moshi = Moshi.Builder().build()
    private val apiService: PaddleOcrApiService
    private val okHttpClient: OkHttpClient
    private val settingsRepository = com.example.data.SettingsRepository(application)

    init {
        checkToken()

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
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

    fun checkToken() {
        viewModelScope.launch {
            val token = settingsRepository.getPaddleOcrToken() ?: ""
            val tokenStatus = if (token.isNotBlank() && !token.startsWith("MY_")) "已配置 (***${token.takeLast(4)})" else "未配置或非法"
            _uiState.value = _uiState.value.copy(tokenStatus = tokenStatus)
        }
    }

    fun setImageUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val tempFile = File(context.cacheDir, "paddle_test_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile
            }
            _uiState.value = _uiState.value.copy(
                imageFile = file,
                requestStatus = "图片已选择",
                httpStatusCode = null,
                jobId = null,
                pollingStatus = "",
                extractedPages = null,
                totalPages = null,
                jsonUrl = null,
                markdownText = null,
                jsonlText = null,
                layoutParsingResultsJson = null,
                errorMsg = null
            )
        }
    }

    fun startTest() {
        val file = _uiState.value.imageFile
        if (file == null) {
            _uiState.value = _uiState.value.copy(errorMsg = "请先选择图片")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val token = settingsRepository.getPaddleOcrToken() ?: ""
            if (token.isBlank() || token.startsWith("MY_")) {
                _uiState.value = _uiState.value.copy(errorMsg = "无效的 token，请配置 PADDLEOCR_TOKEN")
                return@launch
            }

            try {
                _uiState.value = _uiState.value.copy(
                    requestStatus = "正在提交任务...",
                    httpStatusCode = null,
                    rawSubmitResponse = null,
                    rawSubmitErrorBody = null,
                    modelParamsLog = null,
                    jobId = null,
                    errorMsg = null,
                    jsonUrl = null,
                    markdownText = null,
                    jsonlText = null,
                    layoutParsingResultsJson = null,
                    pollingStatus = "not_started"
                )

                val authHeader = "bearer $token"
                val payloadConfig = PaddleOcrSubmitRequest(
                    useDocOrientationClassify = false,
                    useDocUnwarping = false,
                    useChartRecognition = false
                )
                val payloadAdapter = moshi.adapter(PaddleOcrSubmitRequest::class.java)
                val payloadStr = payloadAdapter.toJson(payloadConfig)
                val fileReqBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", file.name, fileReqBody)
                val modelStr = "PaddleOCR-VL-1.6"
                val modelReqBody = modelStr.toRequestBody("text/plain".toMediaTypeOrNull())
                val payloadReqBody = payloadStr.toRequestBody("text/plain".toMediaTypeOrNull())

                val paramsLog = """
                    Token configured: true
                    Token length: ${token.length}
                    Token masked: ${if(token.length > 10) token.take(6) + "******" + token.takeLast(4) else token}
                    model: $modelStr
                    optionalPayload: $payloadStr
                """.trimIndent()

                _uiState.value = _uiState.value.copy(modelParamsLog = paramsLog)

                val submitRes = apiService.submitJob(authHeader, part, modelReqBody, payloadReqBody)
                
                val rawBodyStr = submitRes.body()?.string()
                val rawErrorBodyStr = submitRes.errorBody()?.string()
                
                _uiState.value = _uiState.value.copy(
                    httpStatusCode = submitRes.code(),
                    rawSubmitResponse = rawBodyStr,
                    rawSubmitErrorBody = rawErrorBodyStr
                )
                
                if (!submitRes.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        requestStatus = "submit_failed",
                        pollingStatus = "submit_failed",
                        errorMsg = "HTTP ${submitRes.code()} $rawErrorBodyStr"
                    )
                    return@launch
                }
                
                val jobId = try {
                    val root = org.json.JSONObject(rawBodyStr ?: "{}")
                    val data = root.optJSONObject("data")
                    data?.optString("jobId")?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }

                if (jobId == null) {
                    _uiState.value = _uiState.value.copy(
                        requestStatus = "submit_failed",
                        pollingStatus = "submit_failed",
                        errorMsg = "HTTP 200 but no data.jobId found. Raw response: $rawBodyStr"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    jobId = jobId,
                    requestStatus = "任务已提交，开始轮询...",
                    pollingStatus = "pending"
                )

                delay(3000)
                pollJob(authHeader, jobId)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(requestStatus = "发生异常", errorMsg = e.message)
            }
        }
    }

    fun pollJobManually() {
        val jobId = _uiState.value.jobId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val token = settingsRepository.getPaddleOcrToken() ?: ""
            pollJob("bearer $token", jobId)
        }
    }

    private suspend fun pollJob(authHeader: String, jobId: String) {
        var jobDone = false
        val maxRetries = 40
        for (i in 0 until maxRetries) {
            val statusRes = apiService.getJobStatus(authHeader, jobId)
            val pollUrlStr = "GET https://paddleocr.aistudio-app.com/api/v2/ocr/jobs/$jobId"
            val rawBodyStr = statusRes.body()?.string()
            val rawErrorBodyStr = statusRes.errorBody()?.string()

            _uiState.value = _uiState.value.copy(
                pollUrl = pollUrlStr,
                pollHttpStatusCode = statusRes.code(),
                rawPollResponse = rawBodyStr,
                rawPollErrorBody = rawErrorBodyStr
            )

            if (!statusRes.isSuccessful) {
                _uiState.value = _uiState.value.copy(
                    pollingStatus = "failed",
                    errorMsg = "API Error ${statusRes.code()} $rawErrorBodyStr"
                )
                delay(3000)
                continue
            }
            
            try {
                val root = org.json.JSONObject(rawBodyStr ?: "{}")
                val data = root.optJSONObject("data")
                val state = data?.optString("state").orEmpty()
                val resultUrlObj = data?.optJSONObject("resultUrl")
                val jsonUrlStr = resultUrlObj?.optString("jsonUrl").orEmpty()
                val progress = data?.optJSONObject("extractProgress")
                val totalP = progress?.optInt("totalPages", 0) ?: 0
                val extP = progress?.optInt("extractedPages", 0) ?: 0

                _uiState.value = _uiState.value.copy(
                    pollingStatus = if (state.isBlank()) "poll_response_parse_failed" else state,
                    extractedPages = extP,
                    totalPages = totalP,
                    jsonUrl = if (state == "done") jsonUrlStr else null,
                    errorMsg = data?.optString("errorMsg")?.takeIf { it.isNotBlank() } ?: _uiState.value.errorMsg
                )

                if (state == "done") {
                    jobDone = true
                    if (jsonUrlStr.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            requestStatus = "state=done but data.resultUrl.jsonUrl is empty",
                            errorMsg = "Missing jsonUrl in done state"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(requestStatus = "任务完成，正在解析 JSONL...")
                        downloadAndParseJsonl(jsonUrlStr)
                    }
                    break
                } else if (state == "failed") {
                    _uiState.value = _uiState.value.copy(
                        requestStatus = "任务失败", 
                        errorMsg = root.optString("error").takeIf { it.isNotBlank() } ?: "Unknown API Error"
                    )
                    break
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    pollingStatus = "poll_response_parse_failed",
                    errorMsg = "JSON parse error: ${e.message}"
                )
            }
            delay(3000)
        }

        if (!jobDone && _uiState.value.pollingStatus != "failed" && _uiState.value.pollingStatus != "done") {
            _uiState.value = _uiState.value.copy(requestStatus = "轮询超时")
        }
    }

    private suspend fun downloadAndParseJsonl(url: String) {
        _uiState.value = _uiState.value.copy(
            jsonlDownloadStatus = "Downloading...",
            jsonlParseErrorTrace = null,
            jsonlHttpStatusCode = null,
            jsonlErrorBody = null,
            jsonlBodyLength = null,
            jsonlParsedTableCount = null,
            jsonlFirstTableHtml = null,
            jsonlLedgerRowsCount = null,
            jsonlLedgerRowsPreview = null
        )

        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            
            _uiState.value = _uiState.value.copy(
                jsonlHttpStatusCode = response.code
            )

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                _uiState.value = _uiState.value.copy(
                    jsonlDownloadStatus = "Failed",
                    jsonlErrorBody = errorBody,
                    errorMsg = "Download JSONL failed: ${response.code}"
                )
                return
            }

            val jsonlStr = response.body?.string() ?: ""
            _uiState.value = _uiState.value.copy(
                jsonlDownloadStatus = "Success",
                jsonlBodyLength = jsonlStr.length,
                jsonlText = jsonlStr
            )

            val lines = jsonlStr.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isNotEmpty()) {
                val root = org.json.JSONObject(lines[0])
                val result = root.optJSONObject("result")
                val markdown = result?.optJSONObject("markdown")?.optString("text")

                // Search for tables
                var tableCount = 0
                var firstTableHtml: String? = null

                val layoutParsingResults = result?.optJSONArray("layoutParsingResults")
                if (layoutParsingResults != null) {
                    val htmlTables = mutableListOf<String>()
                    
                    fun extractFrom(json: Any?) {
                        if (json is org.json.JSONArray) {
                            for (i in 0 until json.length()) {
                                extractFrom(json.get(i))
                            }
                        } else if (json is org.json.JSONObject) {
                            val prunedResult = json.optJSONObject("prunedResult")
                            val parsingResList = prunedResult?.optJSONArray("parsing_res_list")
                            if (parsingResList != null) {
                                for (j in 0 until parsingResList.length()) {
                                    val res = parsingResList.optJSONObject(j) ?: continue
                                    val blockLabel = res.optString("block_label")
                                    if (blockLabel == "table") {
                                        val html = res.optString("block_content")
                                        if (html.isNotBlank()) {
                                            htmlTables.add(html)
                                        }
                                    }
                                }
                            } else {
                                // Search values
                                val keys = json.keys()
                                while(keys.hasNext()) {
                                    extractFrom(json.opt(keys.next()))
                                }
                            }
                        }
                    }
                    
                    extractFrom(layoutParsingResults)
                    
                    tableCount = htmlTables.size
                    firstTableHtml = htmlTables.firstOrNull()
                }

                _uiState.value = _uiState.value.copy(
                    markdownText = markdown,
                    layoutParsingResultsJson = layoutParsingResults?.toString(),
                    jsonlParsedTableCount = tableCount,
                    jsonlFirstTableHtml = firstTableHtml?.take(2000),
                    requestStatus = "JSON解析成功，渲染数据中..."
                )

                // Call parser
                val ledgerPage = com.example.ocr.processing.PaddleHtmlTableLedgerParser.parseJsonl(jsonlStr)
                val rows = ledgerPage.rows
                val preview = rows.take(5).joinToString("\n") { r -> 
                    "Date: ${r.dateText}, Left/Right: ${r.sourceSide}, Raw: ${r.deliveryRawText}, Status: ${r.status.name}"
                }

                _uiState.value = _uiState.value.copy(
                    jsonlLedgerRowsCount = rows.size,
                    jsonlLedgerRowsPreview = preview,
                    requestStatus = "表格解析完成"
                )

            } else {
                 _uiState.value = _uiState.value.copy(requestStatus = "JSONL为空", jsonlDownloadStatus = "Empty JSONL")
            }
        } catch (e: Exception) {
             _uiState.value = _uiState.value.copy(
                 jsonlDownloadStatus = "Error",
                 jsonlParseErrorTrace = android.util.Log.getStackTraceString(e),
                 errorMsg = "解析异常: ${e.message}"
             )
        }
    }
}
