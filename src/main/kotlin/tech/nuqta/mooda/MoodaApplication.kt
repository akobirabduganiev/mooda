package tech.nuqta.mooda

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MoodaApplication

fun main(args: Array<String>) {
    runApplication<MoodaApplication>(*args)
}
