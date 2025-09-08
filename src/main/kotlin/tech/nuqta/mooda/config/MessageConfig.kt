package tech.nuqta.mooda.config

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.Locale

@Configuration
class MessageConfig {
    @Bean
    fun messageSource(): MessageSource {
        val ms = ReloadableResourceBundleMessageSource()
        ms.setBasename("classpath:i18n/messages_mooda")
        ms.setDefaultEncoding("UTF-8")
        ms.setFallbackToSystemLocale(false)
        ms.setDefaultLocale(Locale.ENGLISH)
        return ms
    }
}