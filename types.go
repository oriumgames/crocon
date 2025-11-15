package crocon

import "fmt"

// Edition represents a Minecraft edition.
type Edition string

const (
	// JavaEdition represents the Java Edition of Minecraft.
	JavaEdition Edition = "java"
	// BedrockEdition represents the Bedrock Edition of Minecraft.
	BedrockEdition Edition = "bedrock"
)

// ConversionRequest holds the common parameters for any conversion operation.
// It is meant to be embedded in more specific request structs.
type ConversionRequest struct {
	// FromVersion is the source Minecraft version string (e.g., "1.20.4").
	FromVersion string
	// ToVersion is the target Minecraft version string (e.g., "1.20.80").
	ToVersion string
	// FromEdition is the source Minecraft edition (Java or Bedrock).
	FromEdition Edition
	// ToEdition is the target Minecraft edition (Java or Bedrock).
	ToEdition Edition
}

// Block represents a Minecraft block, including its identifier and state properties.
type Block struct {
	// ID is the namespaced identifier of the block (e.g., "minecraft:stone_bricks").
	ID string `nbt:"id"`
	// States is a map of block state properties to their values.
	// For example: {"stone_brick_type": "mossy"}
	States map[string]any `nbt:"states"`
}

// Item represents a Minecraft item stack. It is represented as a map that
// directly corresponds to the item's NBT structure.
type Item map[string]any

// Entity represents a Minecraft entity. It is represented as a map that
// directly corresponds to the entity's NBT structure.
type Entity map[string]any

// BlockEntity represents a Minecraft block entity. It is represented as a map
// that directly corresponds to the block entity's NBT structure.
type BlockEntity map[string]any

// --- Request and Response Structs ---

// BlockRequest defines the parameters for a block conversion.
type BlockRequest struct {
	ConversionRequest
	Block Block
}

// ItemRequest defines the parameters for an item conversion.
type ItemRequest struct {
	ConversionRequest
	Item Item
}

// EntityRequest defines the parameters for an entity conversion.
type EntityRequest struct {
	ConversionRequest
	Entity Entity
}

// BiomeRequest defines the data for a biome conversion.
// For Java -> Bedrock, use Data: `{"name": "minecraft:plains"}`.
// For Bedrock -> Java, use Data: `{"id": 127}`.
type BiomeRequest struct {
	ConversionRequest
	Data map[string]any
}

// BiomeResponse holds the result of a biome conversion.
// The result will contain a Name (if converting to Java) or an ID (if converting to Bedrock).
type BiomeResponse struct {
	Name string `nbt:"name,omitempty"`
	ID   int32  `nbt:"id,omitempty"`
}

// BlockEntityRequest defines the parameters for a block entity conversion.
type BlockEntityRequest struct {
	ConversionRequest
	BlockEntity BlockEntity
}

// --- Error Type ---

// ConversionError represents an error returned from the Crocon native library.
// It includes the Java exception message and a full stack trace for debugging.
type ConversionError struct {
	// Message is the error message from the underlying Java exception.
	Message string
	// StackTrace is the full Java stack trace associated with the error.
	StackTrace string
}

// Error returns the error message, satisfying the Go error interface.
func (e *ConversionError) Error() string {
	return fmt.Sprintf("crocon: conversion failed: %s", e.Message)
}
