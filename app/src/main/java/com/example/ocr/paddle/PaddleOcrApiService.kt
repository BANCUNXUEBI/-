package com.example.ocr.paddle

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Url

interface PaddleOcrApiService {
    @Multipart
    @POST("api/v2/ocr/jobs")
    suspend fun submitJob(
        @Header("Authorization") authHeader: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("optionalPayload") optionalPayload: RequestBody
    ): Response<ResponseBody>

    @GET("api/v2/ocr/jobs/{jobId}")
    suspend fun getJobStatus(
        @Header("Authorization") authHeader: String,
        @Path("jobId") jobId: String
    ): Response<ResponseBody>

    @GET
    suspend fun downloadJsonl(
        @Url url: String
    ): Response<ResponseBody>
}
