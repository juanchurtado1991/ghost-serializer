import re
import sys

def process(file_path):
    with open(file_path, 'r') as f:
        content = f.read()

    # Replace rawChars.concatToString(start, start + length) with rawData.substring(start, start + length)
    content = re.sub(r'rawChars\.concatToString\(([^,]+),\s*([^)]+)\)', r'rawData.substring(\1, \2)', content)
    
    # Replace rawChars with rawData everywhere
    content = content.replace('rawChars', 'rawData')
    
    with open(file_path, 'w') as f:
        f.write(content)

process('ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/parser/GhostJsonStringReader.kt')
process('ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/parser/GhostJsonStringReaderSubsystem.kt')
