package com.example.seefix.ai.core

data class AIImage(
    val uri: String? = null,
    val bytes: ByteArray? = null,
    val mimeType: String = "image/jpeg",
    val timestampMs: Long? = null,
    val description: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AIImage

        if (uri != other.uri) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false
        if (mimeType != other.mimeType) return false
        if (timestampMs != other.timestampMs) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uri?.hashCode() ?: 0
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (timestampMs?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }
}
