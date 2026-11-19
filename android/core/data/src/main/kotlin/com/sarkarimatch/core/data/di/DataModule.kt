package com.sarkarimatch.core.data.di

import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.data.repository.SampleMatchRepository
import com.sarkarimatch.core.eligibility.EligibilityEvaluator
import com.sarkarimatch.core.eligibility.QualificationTaxonomy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun matchRepository(impl: SampleMatchRepository): MatchRepository

    companion object {
        /** Built-in taxonomy until the ReferenceBundle (GET /v1/reference) is cached in Room (FE-11). */
        @Provides
        @Singleton
        fun evaluator(): EligibilityEvaluator = EligibilityEvaluator(QualificationTaxonomy.Builtin)
    }
}
