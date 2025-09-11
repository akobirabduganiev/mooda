package tech.nuqta.mooda.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest
import java.time.Duration

@Service
class MailService(
    @param:Autowired(required = false) private val sesClient: SesV2Client?,
    @param:Value("\${app.mail.from:}") private val fromAddress: String,
    @param:Value("\${app.mail.enabled:true}") private val mailEnabled: Boolean,
    @param:Value("\${app.mail.ses.configuration-set:}") private val sesConfigurationSet: String
) {
    private val log = LoggerFactory.getLogger(MailService::class.java)

    fun sendEmail(to: String, subject: String, body: String): Mono<Void> {
        // Backward-compatible: text-only
        return sendEmail(to = to, subject = subject, textBody = body, htmlBody = null)
    }

    fun sendEmail(to: String, subject: String, textBody: String, htmlBody: String?): Mono<Void> {
        if (!mailEnabled) {
            log.info("Mail disabled by configuration. Skipping send to {} with subject '{}'", to, subject)
            return Mono.empty()
        }
        return sendViaSes(to, subject, textBody, htmlBody)
    }

    private fun sendViaSes(to: String, subject: String, textBody: String, htmlBody: String?): Mono<Void> {
        val client = sesClient ?: run {
            log.warn("SES client is not configured. Email to {} with subject '{}' will be skipped.", to, subject)
            return Mono.empty()
        }
        if (fromAddress.isBlank()) {
            log.warn("'app.mail.from' is not configured for SES. Email to {} with subject '{}' will be skipped.", to, subject)
            return Mono.empty()
        }
        return Mono.fromCallable {
            val destination = Destination.builder().toAddresses(to).build()
            val bodyBuilder = Body.builder()
                .text(Content.builder().data(textBody).charset("UTF-8").build())
            if (!htmlBody.isNullOrBlank()) {
                bodyBuilder.html(Content.builder().data(htmlBody).charset("UTF-8").build())
            }
            val message = Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(bodyBuilder.build())
                .build()
            val emailContent = EmailContent.builder().simple(message).build()
            val reqBuilder = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(destination)
                .content(emailContent)
            if (sesConfigurationSet.isNotBlank()) {
                reqBuilder.configurationSetName(sesConfigurationSet)
            }
            client.sendEmail(reqBuilder.build())
            null
        }
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(20))
            .doOnSuccess { log.info("SES email successfully sent to {} with subject '{}'", to, subject) }
            .doOnError { ex -> log.error("Failed to send SES email to {} with subject '{}': {}", to, subject, ex.message) }
            .onErrorResume { Mono.empty() }
            .then()
    }

}
