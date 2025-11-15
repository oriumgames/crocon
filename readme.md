# crocon

A library for converting Minecraft data types—including blocks, items, and entities—across different game editions and versions.

This Go library is a high-level interface to a native shared library located in the `native/` directory. The shared library is a GraalVM Ahead-of-Time (AOT) compiled artifact of a Java project that wraps the [Chunker](https://github.com/HiveMC/Chunker) API to expose its conversion capabilities over a C ABI.

## Requirements

### For Users

-   A C compiler (like `gcc` or `clang`) for CGo.
-   The pre-compiled `libcrocon` shared library (`.so`, `.dll`, or `.dylib`) available in your library path.

### For Building from Source

-   All of the above.
-   A **GraalVM JDK** (Java 17+) with the `native-image` component installed.
-   The Gradle build tool.

## Installation

```sh
go get github.com/oriumgames/crocon
```

## Quick Start

Here is a simple example of how to initialize the converter and use it to convert a block.

```go
package main

import (
	"errors"
	"fmt"
	"log"

	"github.com/oriumgames/crocon"
)

func main() {
	// 1. Initialize the converter. This creates the GraalVM isolate.
	converter, err := crocon.NewConverter()
	if err != nil {
		log.Fatalf("Failed to initialize converter: %v", err)
	}
	// 2. IMPORTANT: Defer the Close() call to release all resources.
	defer converter.Close()

	log.Println("Converter initialized successfully.")

	// --- Successful Conversion ---
	req := crocon.BlockRequest{
		ConversionRequest: crocon.ConversionRequest{
			FromVersion: "1.20.4",
			ToVersion:   "1.20.80",
			FromEdition: crocon.JavaEdition,
			ToEdition:   crocon.BedrockEdition,
		},
		Block: crocon.Block{
			ID:     "minecraft:mossy_stone_bricks",
			States: make(map[string]interface{}),
		},
	}

	result, err := converter.ConvertBlock(req)
	if err != nil {
		log.Fatalf("Conversion failed unexpectedly: %v", err)
	}

	fmt.Printf("Successfully converted '%s' -> '%s'\n", req.Block.ID, result.ID)
	fmt.Printf("Resulting states: %v\n", result.States)

	// --- Failed Conversion & Error Handling ---
	failedReq := crocon.BlockRequest{
		ConversionRequest: crocon.ConversionRequest{
			FromVersion: "1.21.10",
			ToVersion:   "1.21.120",
			FromEdition: crocon.JavaEdition,
			ToEdition:   crocon.BedrockEdition,
		},
		Block: crocon.Block{ ID: "minecraft:invalid_block" },
	}

	_, err = converter.ConvertBlock(failedReq)
	if err != nil {
		var convErr *crocon.ConversionError
		// Use errors.As to inspect the custom error type
		if errors.As(err, &convErr) {
			fmt.Println("\nCaught an expected conversion error:")
			fmt.Printf("  Message: %s\n", convErr.Message)
			// You can also inspect convErr.StackTrace for deep debugging
		}
	}
}
```

## Building the Native Library

The native shared library (`libcrocon.so`) is compiled from the Java source code located in the `native/` directory. If you make changes to the Java code or need to compile for a different architecture, you can rebuild it using Gradle.

**Prerequisites:** Ensure you have a GraalVM JDK configured on your system `PATH`.

```sh
cd native/

./gradlew build

cd build/libs

native-image   @../../native-image-args.txt  -cp crocon-1.0.jar
```

This will generate a compiled shared library. You must then move this library to a location where your Go application can find it (e.g., `/usr/local/lib` or alongside your Go executable).

The Go wrapper is configured via CGo flags to look for the library in the project root by default.

## Error Handling

The library returns a custom error type, `crocon.ConversionError`, when the underlying Java engine fails a conversion. This error type contains the Java exception message and a full stack trace for debugging. You can inspect it using `errors.As`, as shown in the Quick Start example.

## License

This project is licensed under the MIT License.
