package tech.nuqta.mooda.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

@Service
class MailService(
    @param:Autowired(required = false) private val mailSender: JavaMailSender?,
    @Value("\${app.mail.from:}") private val fromAddress: String,
    @Value("\${app.mail.enabled:true}") private val mailEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(MailService::class.java)

    fun sendEmail(to: String, subject: String, body: String): Mono<Void> {
        if (!mailEnabled) {
            log.info("Mail disabled by configuration. Skipping send to {} with subject '{}'", to, subject)
            return Mono.empty()
        }
        val sender = mailSender ?: run {
            log.warn("JavaMailSender is not configured. Email to {} with subject '{}' will be skipped.", to, subject)
            return Mono.empty()
        }
        return Mono.fromCallable {
            val msg = SimpleMailMessage().apply {
                if (fromAddress.isNotBlank()) this.from = fromAddress
                setTo(to)
                this.subject = subject
                this.text = body
            }
            sender.send(msg)
            null
        }
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(15))
            .doOnSuccess { log.info("Email successfully sent to {} with subject '{}'", to, subject) }
            .doOnError { ex -> log.error("Failed to send email to {} with subject '{}': {}", to, subject, ex.message) }
            .onErrorResume { Mono.empty() }
            .then()
    }
}
