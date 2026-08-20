package com.goodstadt.john.language.exams.packages.UploadJson

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * de + debug ONLY (this file lives in `src/deDebug`). Supplies the real [UploadAdminRepository], which
 * makes the `Optional<UploadAdminRepository>` declared by [UploadAdminModule] resolve to *present* in
 * the German debug variant. No such module exists in other variants, so there the Optional is empty.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UploadAdminDebugModule {

    @Binds
    abstract fun bindUploadAdminRepository(impl: FirestoreUploadAdminRepository): UploadAdminRepository
}
