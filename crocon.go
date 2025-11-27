package crocon

/*
#cgo CFLAGS: -I./include
#cgo LDFLAGS: -lcrocon
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
	"sync"
	"unsafe"

	"github.com/oriumgames/nbt"
)

// conversionRequest represents a task to be executed on the dedicated CGO worker thread.
// This ensures that all interactions with a GraalVM isolate happen on the same OS thread.
type conversionRequest struct {
	// payload holds the input data for the conversion (e.g., BlockRequest).
	payload any
	// convertFunc is the actual CGO function wrapper that performs the conversion.
	convertFunc func(isolateThread *C.graal_isolatethread_t, payload any) (any, error)
	// responseChan is the channel to send the result back to the calling goroutine.
	responseChan chan<- conversionResponse
}

// conversionResponse is the result of a conversion task.
type conversionResponse struct {
	result any
	err    error
}

// Converter provides a safe, idiomatic Go interface to the Crocon native library.
// It manages a dedicated, thread-locked goroutine to handle the lifecycle of the
// GraalVM isolate and all data marshalling, ensuring thread-safe CGO calls.
type Converter struct {
	requestChan    chan conversionRequest
	shutdownChan   chan struct{}
	workerDoneChan chan struct{}
	cache          sync.Map
}

// NewConverter creates a new instance of the converter. It initializes a GraalVM isolate
// on a dedicated OS thread, which is used for all subsequent conversion calls.
// The returned Converter MUST be closed with the Close() method to prevent resource leaks.
func NewConverter() (*Converter, error) {
	converter := &Converter{
		requestChan:    make(chan conversionRequest),
		shutdownChan:   make(chan struct{}),
		workerDoneChan: make(chan struct{}),
	}

	initChan := make(chan error, 1)
	go converter.worker(initChan)

	// Wait for the worker goroutine to successfully initialize the GraalVM isolate.
	if err := <-initChan; err != nil {
		return nil, err
	}

	// Use a Go finalizer as a safety net to ensure the isolate is torn down
	// if the user forgets to call Close().
	runtime.SetFinalizer(converter, (*Converter).Close)

	return converter, nil
}

// worker is the heart of the Converter. It runs on a single, locked OS thread and is
// responsible for all CGO calls to the GraalVM native library.
func (c *Converter) worker(initChan chan<- error) {
	// Lock the goroutine to its current OS thread. This is mandatory for GraalVM isolates.
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	var isolate *C.graal_isolate_t
	var thread *C.graal_isolatethread_t

	if C.graal_create_isolate(nil, &isolate, &thread) != 0 {
		initChan <- fmt.Errorf("failed to create graalvm isolate")
		return
	}
	// Ensure the isolate is torn down when the worker exits.
	defer C.graal_tear_down_isolate(thread)

	// Signal that initialization was successful.
	initChan <- nil

	for {
		select {
		case req := <-c.requestChan:
			// Execute the requested conversion function.
			result, err := req.convertFunc(thread, req.payload)
			req.responseChan <- conversionResponse{result: result, err: err}
		case <-c.shutdownChan:
			// The shutdown signal was received, signal completion and exit.
			c.workerDoneChan <- struct{}{}
			return
		}
	}
}

// Close tears down the GraalVM isolate and releases its resources.
// This method MUST be called when you are finished with the converter.
func (c *Converter) Close() {
	if c.shutdownChan != nil {
		// Signal the worker to shut down.
		close(c.shutdownChan)
		// Wait for the worker to confirm it has cleaned up.
		<-c.workerDoneChan

		// Nil out the channels to prevent further use.
		c.shutdownChan = nil
		c.requestChan = nil
		c.workerDoneChan = nil
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

// marshalRequest handles marshalling the Go request struct to a Base64-encoded string.
// It explicitly uses Bedrock (Little Endian) NBT encoding.
func marshalRequest(request nbtRequest) (string, error) {
	var buf bytes.Buffer
	encoder := nbt.NewEncoderWithEncoding(&buf, nbt.LittleEndian)
	if err := encoder.Encode(request); err != nil {
		return "", fmt.Errorf("failed to marshal request to Bedrock NBT: %w", err)
	}

	return base64.StdEncoding.EncodeToString(buf.Bytes()), nil
}

// processResponseString handles unmarshalling the base64 string response into a Go struct.
func processResponseString(b64Result string, responseData any) error {
	nbtResultBytes, err := base64.StdEncoding.DecodeString(b64Result)
	if err != nil {
		return fmt.Errorf("failed to base64-decode response from library: %w", err)
	}

	decoder := nbt.NewDecoderWithEncoding(bytes.NewReader(nbtResultBytes), nbt.LittleEndian)

	var genericResponse map[string]any
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
		data = genericResponse
	}

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

// dispatch sends a request to the worker and waits for the response.
func (c *Converter) dispatch(payload any, convertFunc func(*C.graal_isolatethread_t, any) (any, error)) (any, error) {
	if c.requestChan == nil {
		return nil, fmt.Errorf("converter has been closed")
	}

	responseChan := make(chan conversionResponse, 1)
	c.requestChan <- conversionRequest{
		payload:      payload,
		convertFunc:  convertFunc,
		responseChan: responseChan,
	}

	response := <-responseChan
	return response.result, response.err
}

// ConvertBlock converts a block between editions.
func (c *Converter) ConvertBlock(req BlockRequest) (*Block, error) {
	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Block,
	}

	b64Input, err := marshalRequest(nbtReq)
	if err != nil {
		return nil, err
	}

	cacheKey := "block:" + b64Input
	if cached, ok := c.cache.Load(cacheKey); ok {
		var responseBlock Block
		if err := processResponseString(cached.(string), &responseBlock); err != nil {
			return nil, err
		}
		return &responseBlock, nil
	}

	res, err := c.dispatch(b64Input, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		cInput := C.CString(payload.(string))
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_block(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_block returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		return C.GoString(cResult), nil
	})

	if err != nil {
		return nil, err
	}

	b64Result := res.(string)
	c.cache.Store(cacheKey, b64Result)

	var responseBlock Block
	if err := processResponseString(b64Result, &responseBlock); err != nil {
		return nil, err
	}
	return &responseBlock, nil
}

// ConvertItem converts an item stack between editions.
func (c *Converter) ConvertItem(req ItemRequest) (*Item, error) {
	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Item,
	}

	b64Input, err := marshalRequest(nbtReq)
	if err != nil {
		return nil, err
	}

	cacheKey := "item:" + b64Input
	if cached, ok := c.cache.Load(cacheKey); ok {
		var responseItem Item
		if err := processResponseString(cached.(string), &responseItem); err != nil {
			return nil, err
		}
		return &responseItem, nil
	}

	res, err := c.dispatch(b64Input, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		cInput := C.CString(payload.(string))
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_item(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_item returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		return C.GoString(cResult), nil
	})

	if err != nil {
		return nil, err
	}

	b64Result := res.(string)
	c.cache.Store(cacheKey, b64Result)

	var responseItem Item
	if err := processResponseString(b64Result, &responseItem); err != nil {
		return nil, err
	}
	return &responseItem, nil
}

// ConvertEntity converts an entity between editions.
func (c *Converter) ConvertEntity(req EntityRequest) (*Entity, error) {
	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Entity,
	}

	b64Input, err := marshalRequest(nbtReq)
	if err != nil {
		return nil, err
	}

	cacheKey := "entity:" + b64Input
	if cached, ok := c.cache.Load(cacheKey); ok {
		var responseEntity Entity
		if err := processResponseString(cached.(string), &responseEntity); err != nil {
			return nil, err
		}
		return &responseEntity, nil
	}

	res, err := c.dispatch(b64Input, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		cInput := C.CString(payload.(string))
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_entity(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_entity returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		return C.GoString(cResult), nil
	})

	if err != nil {
		return nil, err
	}

	b64Result := res.(string)
	c.cache.Store(cacheKey, b64Result)

	var responseEntity Entity
	if err := processResponseString(b64Result, &responseEntity); err != nil {
		return nil, err
	}
	return &responseEntity, nil
}

// ConvertBiome converts a biome identifier between editions.
func (c *Converter) ConvertBiome(req BiomeRequest) (*BiomeResponse, error) {
	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.Data,
	}

	b64Input, err := marshalRequest(nbtReq)
	if err != nil {
		return nil, err
	}

	cacheKey := "biome:" + b64Input
	if cached, ok := c.cache.Load(cacheKey); ok {
		var responseBiome BiomeResponse
		if err := processResponseString(cached.(string), &responseBiome); err != nil {
			return nil, err
		}
		return &responseBiome, nil
	}

	res, err := c.dispatch(b64Input, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		cInput := C.CString(payload.(string))
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_biome(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_biome returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		return C.GoString(cResult), nil
	})

	if err != nil {
		return nil, err
	}

	b64Result := res.(string)
	c.cache.Store(cacheKey, b64Result)

	var responseBiome BiomeResponse
	if err := processResponseString(b64Result, &responseBiome); err != nil {
		return nil, err
	}
	return &responseBiome, nil
}

// ConvertBlockEntity converts a block entity between editions.
func (c *Converter) ConvertBlockEntity(req BlockEntityRequest) (*BlockEntity, error) {
	nbtReq := nbtRequest{
		FromVersion: req.FromVersion, ToVersion: req.ToVersion,
		FromEdition: req.FromEdition, ToEdition: req.ToEdition,
		Data: req.BlockEntity,
	}

	b64Input, err := marshalRequest(nbtReq)
	if err != nil {
		return nil, err
	}

	cacheKey := "block_entity:" + b64Input
	if cached, ok := c.cache.Load(cacheKey); ok {
		var responseBlockEntity BlockEntity
		if err := processResponseString(cached.(string), &responseBlockEntity); err != nil {
			return nil, err
		}
		return &responseBlockEntity, nil
	}

	res, err := c.dispatch(b64Input, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		cInput := C.CString(payload.(string))
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_block_entity(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_block_entity returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		return C.GoString(cResult), nil
	})

	if err != nil {
		return nil, err
	}

	b64Result := res.(string)
	c.cache.Store(cacheKey, b64Result)

	var responseBlockEntity BlockEntity
	if err := processResponseString(b64Result, &responseBlockEntity); err != nil {
		return nil, err
	}
	return &responseBlockEntity, nil
}
