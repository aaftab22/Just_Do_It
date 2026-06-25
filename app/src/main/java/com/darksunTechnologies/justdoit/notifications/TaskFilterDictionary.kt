package com.darksunTechnologies.justdoit.notifications

/**
 * Single source of truth for all filter lists, keyword sets, and regex patterns
 * used by SmartParseRouter (pipeline filtering + intent scoring + regex extraction).
 *
 * Consolidated from the former SmartTaskParser and SmartParseRouter duplicate lists.
 * NO other file should define inline filter sets — reference this dictionary instead.
 */
object TaskFilterDictionary {

    // ══════════════════════════════════════════════════════════════
    // ── STAGE 1: MEDIA / JUNK FILTER ─────────────────────────────
    // ══════════════════════════════════════════════════════════════

    /** Patterns that indicate the notification is media, a call, or system noise — not text content. */
    val MEDIA_JUNK = setOf(
        // Media attachments
        "📷 photo", "gif", "🎥 video", "🎤 voice message", "sticker", "📎",
        "shared contact", "shared location", "location", "🔗",
        // Call-related
        "missed voice call", "missed video call", "incoming voice call", "incoming video call",
        "video call", "audio call", "voice call", "missed call",
        // System noise
        "this message was deleted", "waiting for this message",
        "checking for new messages", "whatsapp web is currently active"
    )

    /** Regex to match "N new messages" summaries. */
    val N_MESSAGES_REGEX = Regex("\\d+ new messages?")

    // ══════════════════════════════════════════════════════════════
    // ── STAGE 2: NEGATIVE PATTERN FILTER ─────────────────────────
    // ══════════════════════════════════════════════════════════════

    /** Short conversational responses and greetings — not actionable tasks. */
    val ACKNOWLEDGMENTS = setOf(
        "ok", "okay", "k", "kk", "cool", "nice", "thanks", "thank you",
        "got it", "sounds good", "perfect", "great", "awesome", "sure",
        "yep", "yeah", "yea", "nah", "nope", "lol", "lmao", "haha", "hehe", "omg", "wow",
        "good morning", "good night", "happy birthday", "congratulations", "congrats"
    )

    /** Sender-perspective statements — the sender is reporting what THEY did, not requesting action. */
    val SENDER_STATEMENTS = listOf(
        Regex("^i (will|am|was|have|had|did|just|already|sent|paid|bought|called)"),
        Regex("^we (will|are|were|have|had|did|just|already|sent|paid|bought|called)"),
        Regex("^it (went|was|is|has|had)")
    )

    /** E-commerce, OTP, and promotional spam patterns. */
    val SPAM_COMMERCE = setOf(
        "delivered", "shipped", "tracking", "otp", "offer", "discount",
        "% off", "sale", "coupon", "promo", "subscribe", "unsubscribe",
        "order", "will be delivered", "has been shipped"
    )

    /** Conversational tones that imply the speaker is NOT assigning a task to the recipient. */
    val NON_TASK_TONES = listOf("you can", "you should", "do not", "don't")

    /** Message patterns that indicate a question, not a command. */
    val QUESTION_FILTERS = listOf("?", "will you", "are you", "would you")

    // ══════════════════════════════════════════════════════════════
    // ── STAGE 3: INTENT SCORING ──────────────────────────────────
    // ══════════════════════════════════════════════════════════════

    /** Action verbs that indicate a direct task command when appearing at the start of a message. (+3 points) */
    val ACTION_VERBS = setOf(
        "buy", "call", "send", "pick", "bring", "meet", "pay", "book",
        "take", "go", "remind", "schedule", "return", "submit", "complete",
        "finish", "deliver", "transfer", "reply", "forward", "email", "text",
        "message", "deposit", "withdraw", "pickup", "read"
    )

    /** Phrases that express task intent regardless of language or verb position. (+3 points) */
    val INTENT_PHRASES = setOf(
        "don't forget", "remember to", "make sure", "need to", "have to",
        "gotta", "must", "should", "supposed to", "reminder",
        "let's", "let s",
        // Hinglish
        "yaad", "bhulna mat", "zaroor"
    )

    /** References to future time — suggest the message describes a future action. (+2 points) */
    val FUTURE_TIME = setOf(
        "tomorrow", "tonight", "next week", "this weekend",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "morning", "afternoon", "evening",
        "by eod", "by end of day",
        // Hinglish
        "kal", "aaj", "parso", "subah", "shaam", "dopahar"
    )

    /** Urgency and obligation markers. (+1 point) */
    val OBLIGATION = setOf(
        "please", "plz", "pls", "asap", "urgent", "deadline", "due",
        "pending", "follow up", "waiting for",
        // Hinglish
        "jaldi", "turant"
    )

    /** Second-person directed language — indicates the message is asking someone to do something. (+1 point) */
    val SECOND_PERSON = setOf(
        "you need", "can you", "could you", "would you", "please do",
        // Hinglish
        "tum", "aap"
    )

    /** Keywords that indicate high priority on the extracted task. */
    val PRIORITY_KEYWORDS = setOf("urgent", "asap", "important", "emergency")

    // ══════════════════════════════════════════════════════════════
    // ── TIME EXTRACTION REGEXES ──────────────────────────────────
    // ══════════════════════════════════════════════════════════════

    /** Matches "5 pm", "5:30 PM", "11.45am" */
    val STANDARD_TIME_REGEX = Regex("\\b(1[0-2]|[1-9])(?:[:.]([0-5][0-9]))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)

    /** Matches "17:00", "09:30" */
    val MILITARY_TIME_REGEX = Regex("\\b([0-1]?[0-9]|2[0-3]):([0-5][0-9])\\b")

    /** Matches "at 7", "at 11" — weak time with assumed PM for daytime hours. */
    val WEAK_TIME_REGEX = Regex("\\bat\\s+(1[0-2]|[1-9])\\b", RegexOption.IGNORE_CASE)

    /** Day of week regex for date extraction. */
    val DAY_OF_WEEK_REGEX = Regex("\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE)
}
