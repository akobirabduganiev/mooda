package tech.nuqta.mooda.domain.model

enum class MoodType(val defaultEmoji: String) {
    HAPPY("😊"),
    CALM("😌"),
    EXCITED("🤩"),
    TIRED("🥱"),
    STRESSED("😣"),
    SAD("😢"),
    ANGRY("😠"),
    GRATEFUL("🙏");

    companion object {
        fun from(code: String): MoodType = try {
            valueOf(code.uppercase())
        } catch (e: IllegalArgumentException) {
            throw InvalidMoodType(code)
        }
    }
}

class InvalidMoodType(val code: String) : RuntimeException("Invalid mood type: $code")