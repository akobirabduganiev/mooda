package tech.nuqta.mooda.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client

@Configuration
class MailConfig {
    private val log = LoggerFactory.getLogger(MailConfig::class.java)

    @Bean
    @Lazy
    @ConditionalOnProperty(prefix = "app.mail", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun sesV2Client(
        @Value("\${app.mail.ses.region:}") region: String,
        @Value("\${app.mail.ses.access-key-id:}") accessKeyId: String,
        @Value("\${app.mail.ses.secret-access-key:}") secretAccessKey: String,
        @Value("\${app.mail.ses.session-token:}") sessionToken: String,
    ): SesV2Client {
        val builder = SesV2Client.builder()
        val credsProvider = if (accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) {
            if (sessionToken.isNotBlank()) {
                StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken))
            } else {
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
            }
        } else {
            DefaultCredentialsProvider.create()
        }
        builder.credentialsProvider(credsProvider)
        if (region.isNotBlank()) {
            builder.region(Region.of(region))
        } else {
            log.info("SES region not set in app.mail.ses.region. Falling back to AWS SDK defaults (e.g., AWS_REGION env var)")
        }
        return builder.build()
    }
}
