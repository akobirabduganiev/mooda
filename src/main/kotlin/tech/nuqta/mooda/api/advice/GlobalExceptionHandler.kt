package tech.nuqta.mooda.api.advice

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.model.InvalidMoodType
import java.net.URI

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidMoodType::class)
    fun handleInvalidMood(e: InvalidMoodType): Mono<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.message ?: "Invalid mood type")
        pd.type = URI.create("https://api.mooda.tech/problems/invalid-mood-type")
        pd.title = "Invalid mood type"
        pd.setProperty("code", "invalid_mood_type")
        pd.setProperty("invalidValue", e.code)
        return Mono.just(pd)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): Mono<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Bad request")
        pd.title = "Bad Request"
        return Mono.just(pd)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException): Mono<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(e.statusCode, e.reason ?: e.message ?: "Error")
        pd.title = e.statusCode.toString()
        e.reason?.let { pd.setProperty("code", it) }
        return Mono.just(pd)
    }

    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponse(e: ErrorResponseException): Mono<ProblemDetail> = Mono.just(e.body)

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): Mono<ProblemDetail> {
        val detail = e.message ?: "Unexpected error"
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail)
        pd.title = "Internal Server Error"
        pd.setProperty("code", "unexpected_error")
        pd.setProperty("exception", e.javaClass.name)
        e.cause?.let { pd.setProperty("cause", it.message ?: it.javaClass.name) }
        return Mono.just(pd)
    }
}