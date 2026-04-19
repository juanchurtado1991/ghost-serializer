package com.ghostserializer.core.parser

enum class JsonToken {
    BEGIN_OBJECT,
    END_OBJECT,
    BEGIN_ARRAY,
    END_ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    END_DOCUMENT
}
