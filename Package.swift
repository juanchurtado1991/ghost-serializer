// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "GhostSerialization",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "GhostSerialization",
            targets: ["GhostSerialization"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "GhostSerialization",
            url: "https://github.com/juanchurtado1991/GhostSerialization/releases/download/v1.1.0/GhostSerialization.xcframework.zip",
            checksum: "DEBES_REEMPLAZAR_ESTO_CON_EL_CHECKSUM"
        )
    ]
)
