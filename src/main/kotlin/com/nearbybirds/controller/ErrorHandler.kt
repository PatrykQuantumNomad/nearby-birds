package com.nearbybirds.controller

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)

@RestControllerAdvice
class ErrorHandler {
    private val logger = LoggerFactory.getLogger(ErrorHandler::class.java)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val messages = ex.constraintViolations.joinToString("; ") { it.message }
        return ResponseEntity.badRequest().body(
            ErrorResponse("validation_error", messages, 400)
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse("missing_parameter", "Required parameter '${ex.parameterName}' is missing", 400)
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse("invalid_parameter", "Parameter '${ex.name}' must be of type ${ex.requiredType?.simpleName}", 400)
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse("internal_error", "An unexpected error occurred", 500)
        )
    }
}
