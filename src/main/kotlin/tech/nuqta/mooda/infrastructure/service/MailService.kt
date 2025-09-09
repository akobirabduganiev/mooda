package tech.nuqta.mooda.infrastructure.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class MailService(@Autowired(required = false) private val mailSender: JavaMailSender?) {
    fun sendEmail(to: String, subject: String, body: String): Mono<Void> {
        val sender = mailSender ?: return Mono.empty()
        return Mono.fromRunnable<Unit> {
            val msg = SimpleMailMessage()
            msg.setTo(to)
            msg.subject = subject
            msg.text = body
            sender.send(msg)
        }.then()
    }
}
