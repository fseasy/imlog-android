//buildscript {
//    configurations.all {
//        resolutionStrategy {
//            force("org.jetbrains:annotations:23.0.0")
//        }
//    }
//}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.hilt) apply false
}
