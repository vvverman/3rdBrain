// swift-tools-version: 6.0
import PackageDescription
let package = Package(name: "BrainCore", products: [.library(name: "BrainCore", targets: ["BrainCore"])], targets: [
    .target(name: "BrainCore", path: "ThirdBrain/Core"),
    .testTarget(name: "BrainCoreTests", dependencies: ["BrainCore"], path: "CoreTests")
])
