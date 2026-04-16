import os

from_dir = "/home/juan/AndroidStudioProjects/GhostSerialization/ghost-core/src/commonTest/kotlin/com/ghost/serialization/core"

more_imports = """
import com.ghost.serialization.core.parser.skipCommaIfPresent
import com.ghost.serialization.core.parser.nextNonWhitespace
import com.ghost.serialization.core.parser.skipAnyValue
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer
"""

def unique_imports(contents):
    lines = contents.split("\n")
    out = []
    imports = set()
    for line in lines:
        if line.startswith("import "):
            if line not in imports:
                imports.add(line)
                out.append(line)
        else:
            out.append(line)
    return "\n".join(out)

for root, _, files in os.walk(from_dir):
    for f in files:
        if f.endswith(".kt"):
            path = os.path.join(root, f)
            with open(path, "r") as file:
                contents = file.read()
            
            # insert more imports after package
            contents = contents.replace("package com.ghost.serialization.core\n", "package com.ghost.serialization.core\n" + more_imports)
            
            # clean up duplicate imports
            contents = unique_imports(contents)

            with open(path, "w") as file:
                file.write(contents)

print("More imports added to test files and duplicates cleaned.")
