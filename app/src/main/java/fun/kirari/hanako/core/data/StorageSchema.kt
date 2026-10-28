package `fun`.kirari.hanako.core.data

/** Versioned data semantics, independent from the APK version and any future Room schema. */
object StorageSchema {
    const val CURRENT_APP_DATA_VERSION = 3
    const val ANSWER_VERSION_IDS_MIGRATION = "history-answer-version-ids-v1"
    const val QUOTED_FRAGMENTS_MIGRATION = "history-quoted-fragments-v1"

    val completedMigrations: Set<String> = setOf(
        ANSWER_VERSION_IDS_MIGRATION,
        QUOTED_FRAGMENTS_MIGRATION
    )
}
