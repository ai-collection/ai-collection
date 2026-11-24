package com.vault.calc.data

data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val addedAt: Long
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    enum class Category { PHOTO, FILE }

    val category: Category
        get() = if (isImage || isVideo) Category.PHOTO else Category.FILE
}
