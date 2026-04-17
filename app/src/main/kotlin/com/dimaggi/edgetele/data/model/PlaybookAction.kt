package com.dimaggi.edgetele.data.model

data class PlaybookAction(
    val id: String,
    val action: String,             // localized action text
    val priority: Int,              // 1 (highest) – 5
    val tags: List<String>
) : Comparable<PlaybookAction> {
    override fun compareTo(other: PlaybookAction): Int =
        this.priority.compareTo(other.priority)
}
