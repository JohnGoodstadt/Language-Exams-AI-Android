package com.goodstadt.john.language.exams.packages.UploadJson

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Declares [UploadAdminRepository] as an OPTIONAL Hilt dependency. Only the `src/deDebug` source set
 * supplies a real binding (UploadAdminDebugModule), so `Optional<UploadAdminRepository>` is present in
 * German debug builds and empty in every other variant - which is how the administrative Firestore
 * code stays out of release and non-`de` builds while `main` can still compile against it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UploadAdminModule {

    @BindsOptionalOf
    abstract fun bindOptionalUploadAdminRepository(): UploadAdminRepository
}
