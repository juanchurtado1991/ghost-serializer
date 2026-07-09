package com.ghost.serialization.sample.model

import com.ghost.serialization.sample.model.EngineResult
import com.ghost.serialization.sample.model.OpenLibraryBook

data class BooksLabResult(
    val books: List<OpenLibraryBook>,
    val standardJson: String,
    val proto3Json: String,
    val benchmarkResults: List<EngineResult>
)
