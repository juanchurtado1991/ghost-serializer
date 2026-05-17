package com.ghost.benchmark

data class TestResult(
    val name: String,
    val category: String,
    val passed: Boolean,
    val error: String? = null
)