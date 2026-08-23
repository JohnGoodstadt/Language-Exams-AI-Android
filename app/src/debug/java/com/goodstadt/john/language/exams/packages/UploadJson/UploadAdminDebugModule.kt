package com.goodstadt.john.language.exams.packages.UploadJson

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DEBUG builds ONLY (this file lives in `src/debug`, so it compiles into every flavour's debug variant
 * - enDebug / deDebug / zhDebug - but never staging or release). Supplies the real
 * [UploadAdminRepository], which makes the `Optional<UploadAdminRepository>` declared by
 * [UploadAdminModule] resolve to *present* in any debug build. In staging/release no such module exists,
 * so the Optional is empty and the admin screen reports the library as unavailable.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UploadAdminDebugModule {

    @Binds
    abstract fun bindUploadAdminRepository(impl: FirestoreUploadAdminRepository): UploadAdminRepository
}
