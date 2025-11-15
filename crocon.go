package crocon

/*
#cgo CFLAGS: -I./include
#cgo LDFLAGS: -L. -lcrocon -Wl,-rpath=.
#include <stdlib.h>
#include "crocon.h"
#include "graal_isolate.h"
*/
import "C"

import (
	"bytes"
	"encoding/base64"
	"fmt"
	"runtime"
	"unsafe"

	"github.com/oriumgames/nbt"
)

// Converter provides a safe, idiomatic Go interface to the Crocon native library.
// It handles the lifecycle of the GraalVM isolate and all data marshalling.
type Converter struct {
	isolateThread *C.graal_isolatethread_t
}

// NewConverter creates a new instance of the converter and initializes a GraalVM isolate.
// The returned Converter MUST be closed with the Close() method to prevent resource leaks.
func NewConverter() (*Converter, error) {
	var isolate *C.graal_isolate_t
	var thread *C.graal_isolatethread_t

	// C.graal_create_isolate expects pointers to the pointers that will hold the created isolate/thread.
	if C.graal_create_isolate(nil, &isolate, &thread) != 0 {
		return nil, fmt.Errorf("failed to create graalvm isolate")
	}

	converter := &Converter{
		isolateThread: thread,
	}

	// Use a Go finalizer as a safety net to ensure the isolate is torn down if the user forgets to call Close().
	runtime.SetFinalizer(converter, (*Converter).Close)

	return converter, nil
}

// Close tears down the GraalVM isolate and releases its resources.
// This method MUST be called when you are finished with the converter.
func (c *Converter) Close() {
	if c.isolateThread != nil {
		C.graal_tear_down_isolate(c.isolateThread)
		c.isolateThread = nil
	}
	// Prevent the finalizer from running again after an explicit Close().
	runtime.SetFinalizer(c, nil)
}

// Private struct for the top-level NBT request structure.
type nbtRequest struct {
	FromVersion string  `nbt:"fromVersion"`
	ToVersion   string  `nbt:"toVersion"`
	FromEdition Edition `nbt:"fromEdition"`
	ToEdition   Edition `nbt:"toEdition"`
	Data        any     `nbt:"data"`
}

// prepareRequest handles marshalling the Go request struct to a Base64-encoded C-style string.
// It explicitly uses Bedrock (Little Endian) NBT encoding, which is required by Chunker.
func (c *Converter) prepareRequest(request nbtRequest) (*C.char, error) {
	var buf bytes.Buffer
	// The native library's NBT parser (from Chunker) expects Little Endian (Bedrock) encoding.
	encoder := nbt.NewEncoderWithEncoding(&buf, nbt.LittleEndian)
	if err := encoder.Encode(request); err != nil {
		return nil, fmt.Errorf("failed to marshal request to Bedrock NBT: %w", err)
	}

	b64Input := base64.StdEncoding.EncodeToString(buf.Bytes())
	return C.CString(b64Input), nil
}

// processResponse handles unmarshalling the C-style string response into a Go struct.
// It explicitly uses Bedrock (Little Endian) NBT encoding.
func (c *Converter) processResponse(cResult *C.char, responseData any) error {
	b64Result := C.GoString(cResult)
	nbtResultBytes, err := base64.StdEncoding.DecodeString(b64Result)
	if err != nil {
		return fmt.Errorf("failed to base64-decode response from library: %w", err)
	}

	// Use a decoder that expects Little Endian (Bedrock) encoding.
	decoder := nbt.NewDecoderWithEncoding(bytes.NewReader(nbtResultBytes), nbt.LittleEndian)

	var genericResponse map[string]interface{}
	if err := decoder.Decode(&genericResponse); err != nil {
		return fmt.Errorf("failed to unmarshal NBT response envelope: %w", err)
	}

	if success, ok := genericResponse["success"].(byte); !ok || success == 0 {
		errMsg, _ := genericResponse["error"].(string)
		stackTrace, _ := genericResponse["stackTrace"].(string)
		return &ConversionError{
			Message:    errMsg,
			StackTrace: stackTrace,
		}
	}

	data, ok := genericResponse["data"]
	if !ok {
		// This can happen with biome conversions that return non-map data.
		// We'll try to unmarshal the whole response in that case.
		data = genericResponse
	}

	// To robustly unmarshal the 'data' payload into the specific response struct,
	// we re-marshal just that part and then unmarshal it into the target.
	var dataBuf bytes.Buffer
	dataEncoder := nbt.NewEncoderWithEncoding(&dataBuf, nbt.LittleEndian)
	if err := dataEncoder.Encode(data); err != nil {
		return fmt.Errorf("failed to re-marshal 'data' field for final unmarshaling: %w", err)
	}

	dataDecoder := nbt.NewDecoderWithEncoding(bytes.NewReader(dataBuf.Bytes()), nbt.LittleEndian)
	if err := dataDecoder.Decode(responseData); err != nil {
		return fmt.Errorf("failed to unmarshal 'data' payload into response struct: %w", err)
	}

	return nil
}

// ConvertBlock converts a block between editions.
func (c *Converter) ConvertBlock(req BlockRequest) (*Block, error) {
	if c.isolateThread == nil {
		return nil, fmt.Errorf("converter has been closed")
	}

	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Block,
	}

	cInput, err := c.prepareRequest(nbtReq)
	if err != nil {
		return nil, err
	}
	defer C.free(unsafe.Pointer(cInput))

	cResult := C.convert_block(c.isolateThread, cInput)
	if cResult == nil {
		return nil, fmt.Errorf("cgo call to convert_block returned a null pointer")
	}
	defer C.free_result(c.isolateThread, cResult)

	var responseBlock Block
	if err := c.processResponse(cResult, &responseBlock); err != nil {
		return nil, err
	}
	return &responseBlock, nil
}

// ConvertItem converts an item stack between editions.
func (c *Converter) ConvertItem(req ItemRequest) (*Item, error) {
	if c.isolateThread == nil {
		return nil, fmt.Errorf("converter has been closed")
	}

	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Item,
	}

	cInput, err := c.prepareRequest(nbtReq)
	if err != nil {
		return nil, err
	}
	defer C.free(unsafe.Pointer(cInput))

	cResult := C.convert_item(c.isolateThread, cInput)
	if cResult == nil {
		return nil, fmt.Errorf("cgo call to convert_item returned a null pointer")
	}
	defer C.free_result(c.isolateThread, cResult)

	var responseItem Item
	if err := c.processResponse(cResult, &responseItem); err != nil {
		return nil, err
	}
	return &responseItem, nil
}

// ConvertEntity converts an entity between editions.
func (c *Converter) ConvertEntity(req EntityRequest) (*Entity, error) {
	if c.isolateThread == nil {
		return nil, fmt.Errorf("converter has been closed")
	}

	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Entity,
	}

	cInput, err := c.prepareRequest(nbtReq)
	if err != nil {
		return nil, err
	}
	defer C.free(unsafe.Pointer(cInput))

	cResult := C.convert_entity(c.isolateThread, cInput)
	if cResult == nil {
		return nil, fmt.Errorf("cgo call to convert_entity returned a null pointer")
	}
	defer C.free_result(c.isolateThread, cResult)

	var responseEntity Entity
	if err := c.processResponse(cResult, &responseEntity); err != nil {
		return nil, err
	}
	return &responseEntity, nil
}

// ConvertBiome converts a biome identifier between editions.
func (c *Converter) ConvertBiome(req BiomeRequest) (*BiomeResponse, error) {
	if c.isolateThread == nil {
		return nil, fmt.Errorf("converter has been closed")
	}

	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Data,
	}

	cInput, err := c.prepareRequest(nbtReq)
	if err != nil {
		return nil, err
	}
	defer C.free(unsafe.Pointer(cInput))

	cResult := C.convert_biome(c.isolateThread, cInput)
	if cResult == nil {
		return nil, fmt.Errorf("cgo call to convert_biome returned a null pointer")
	}
	defer C.free_result(c.isolateThread, cResult)

	var responseBiome BiomeResponse
	if err := c.processResponse(cResult, &responseBiome); err != nil {
		return nil, err
	}
	return &responseBiome, nil
}

// ConvertBlockEntity converts a block entity between editions.
func (c *Converter) ConvertBlockEntity(req BlockEntityRequest) (*BlockEntity, error) {
	if c.isolateThread == nil {
		return nil, fmt.Errorf("converter has been closed")
	}

	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.BlockEntity,
	}

	cInput, err := c.prepareRequest(nbtReq)
	if err != nil {
		return nil, err
	}
	defer C.free(unsafe.Pointer(cInput))

	cResult := C.convert_block_entity(c.isolateThread, cInput)
	if cResult == nil {
		return nil, fmt.Errorf("cgo call to convert_block_entity returned a null pointer")
	}
	defer C.free_result(c.isolateThread, cResult)

	var responseBlockEntity BlockEntity
	if err := c.processResponse(cResult, &responseBlockEntity); err != nil {
		return nil, err
	}
	return &responseBlockEntity, nil
}
