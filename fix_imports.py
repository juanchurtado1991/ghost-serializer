import os

from_dir = "/home/juan/AndroidStudioProjects/GhostSerialization/ghost-core/src/commonTest/kotlin/com/ghost/serialization/core"

imports_to_add = """
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.isNextNullValue
import com.ghost.serialization.core.parser.skipValue
import com.ghost.serialization.core.parser.selectName
import com.ghost.serialization.core.parser.JsonToken
import com.ghost.serialization.core.parser.peekJsonToken
import com.ghost.serialization.core.parser.readList
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextDouble
import com.ghost.serialization.core.parser.nextString
import com.ghost.serialization.core.parser.consumeArraySeparator
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.nextFloat
import com.ghost.serialization.core.parser.nextBoolean
import com.ghost.serialization.core.parser.consumeNull
"""

for root, _, files in os.walk(from_dir):
    for f in files:
        if f.endswith(".kt"):
            path = os.path.join(root, f)
            with open(path, "r") as file:
                contents = file.read()
            
            # Avoid duplicate injection
            if "import com.ghost.serialization.core.parser.GhostJsonReader" not in contents:
                contents = contents.replace("package com.ghost.serialization.core\n", "package com.ghost.serialization.core\n" + imports_to_add)
                with open(path, "w") as file:
                    file.write(contents)

print("Imports added to test files.")
