package com.resort_cloud.nansei.nansei_tablet.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.resort_cloud.nansei.nansei_tablet.R
import com.resort_cloud.nansei.nansei_tablet.data.repository.ApiException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Utility class for handling API errors
 * Provides common error handling and dialog display
 */
object ErrorHandler {

    fun handleError(
        context: Context,
        throwable: Throwable,
        onDismiss: (() -> Unit)? = null
    ) {

        val exception = throwableToException(throwable)
        val errorMessage = getErrorMessage(exception, context)
        showErrorDialog(context, errorMessage, onDismiss)
    }

    fun getErrorMessage(exception: Exception, context: Context): String {
        return when (exception) {
            is ApiException -> {
                exception.message ?: context.getString(R.string.unexpected_message)
            }

            is SocketTimeoutException -> {
                context.getString(R.string.timeout_message)
            }

            is UnknownHostException -> {
                context.getString(R.string.unexpected_message)
            }

            is IOException -> {
                context.getString(R.string.connection_error_message)
            }

            else -> {
                context.getString(R.string.unexpected_message)
            }
        }
    }

    /**
     * Show error dialog with message
     */
    fun showErrorDialog(
        context: Context,
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .setCancelable(true)
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .show()
    }

    fun throwableToException(t: Throwable): Exception {
        return t as? Exception ?: Exception(t)
    }

}

