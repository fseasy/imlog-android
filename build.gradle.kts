import nl.littlerobots.vcu.plugin.resolver.VersionSelectors

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.version.catalog.update)
}

versionCatalogUpdate {
    // sort the catalog by key (default is true)
    sortByKey.set(true)
    // Referenced that are pinned are not automatically updated.
    // They are also not automatically kept however (use keep for that).
    pin {

    }
    keep {
        keepUnusedVersions.set(true)
    }

    // Return true in the version selector function to accept the updated version
    // For more details refer to the chapter earlier in this README
    versionSelector(VersionSelectors.STABLE)
}