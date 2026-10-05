package com.kairo.app.ai

enum class ParseSource { CLOUD, LOCAL }

/** Something the user should hear about even though their command was still handled offline. */
sealed interface ParseNotice {
    data class QuotaExceeded(val message: String?) : ParseNotice
    data object Unauthorized : ParseNotice
}

data class ParseOutcome(val result: ParseResult, val source: ParseSource, val notice: ParseNotice? = null)

/**
 * Cloud first when AI is on and set up; otherwise, or when the cloud is slow, offline, over quota
 * or erroring, the offline parser answers. A cloud "unclear" is final: the model understood the
 * sentence well enough to know it isn't a safe command, so we don't second-guess it offline.
 */
class CommandParserFacade(
    private val cloud: CommandParser,
    private val local: CommandParser,
    private val settings: suspend () -> AiSettings,
) : CommandParser {

    override suspend fun parse(text: String): ParseResult = parseDetailed(text).result

    suspend fun parseDetailed(text: String): ParseOutcome {
        if (!settings().isReady) return ParseOutcome(local.parse(text), ParseSource.LOCAL)
        return when (val cloudResult = cloud.parse(text)) {
            is ParseResult.Parsed, is ParseResult.Unclear -> ParseOutcome(cloudResult, ParseSource.CLOUD)
            is ParseResult.Failed -> ParseOutcome(local.parse(text), ParseSource.LOCAL, noticeFor(cloudResult.reason))
        }
    }

    private fun noticeFor(reason: FailReason): ParseNotice? = when (reason) {
        is FailReason.QuotaExceeded -> ParseNotice.QuotaExceeded(reason.message)
        FailReason.Unauthorized -> ParseNotice.Unauthorized
        // Timeouts, no network and server hiccups fall back silently: the user just sees their diff.
        else -> null
    }
}
