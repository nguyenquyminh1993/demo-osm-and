package com.resort_cloud.nansei.nansei_tablet.data.model

import com.google.gson.annotations.SerializedName

/**
 * Error response model from API
 * Structure:
 * {
 *   "success": false,
 *   "timestamp": "20251218170532",
 *   "error_code": 500,
 *   "detail_code": 2101,
 *   "error_msg": "システムエラーが発生いたしました。\nしばらく待ってから再度お試しください。"
 * }
 */
data class ErrorResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("error_code")
    val errorCode: Int?,
    
    @SerializedName("detail_code")
    val detailCode: Int?,
    
    @SerializedName("error_msg")
    val errorMsg: String?
)

