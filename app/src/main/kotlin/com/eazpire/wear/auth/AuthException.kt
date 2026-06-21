package com.eazpire.wear.auth

class AuthException(message: String) : Exception(message)

object AuthErrorMessages {
    fun fromThrowable(e: Throwable): String =
        when (e) {
            is AuthException -> e.message ?: "Authentication failed"
            else -> e.message ?: "Authentication failed"
        }
}
