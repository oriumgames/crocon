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

// prepareRequest handles marshalling the Go request struct to a Base64-encoded C-style string.
// It explicitly uses Bedrock (Little Endian) NBT encoding.
func prepareRequest(request nbtRequest) (*C.char, error) {
	var buf bytes.Buffer
	encoder := nbt.NewEncoderWithEncoding(&buf, nbt.LittleEndian)
	if err := encoder.Encode(request); err != nil {
		return nil, fmt.Errorf("failed to marshal request to Bedrock NBT: %w", err)
	}

	b64Input := base64.StdEncoding.EncodeToString(buf.Bytes())
	return C.CString(b64Input), nil
}

// processResponse handles unmarshalling the C-style string response into a Go struct.
func processResponse(cResult *C.char, responseData any) error {
	b64Result := C.GoString(cResult)
	nbtResultBytes, err := base64.StdEncoding.DecodeString(b64Result)
	if err != nil {
		return fmt.Errorf("failed to base64-decode response from library: %w", err)
	}

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
	res, err := c.dispatch(req, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		req := payload.(BlockRequest)
		nbtReq := nbtRequest{
			FromVersion: req.FromVersion, ToVersion: req.ToVersion,
			FromEdition: req.FromEdition, ToEdition: req.ToEdition,
			Data: req.Block,
		}

		cInput, err := prepareRequest(nbtReq)
		if err != nil {
			return nil, err
		}
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_block(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_block returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		var responseBlock Block
		if err := processResponse(cResult, &responseBlock); err != nil {
			return nil, err
		}
		return &responseBlock, nil
	})

	if err != nil {
		return nil, err
	}
	return res.(*Block), nil
}

// ConvertItem converts an item stack between editions.
func (c *Converter) ConvertItem(req ItemRequest) (*Item, error) {
	res, err := c.dispatch(req, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		req := payload.(ItemRequest)
		nbtReq := nbtRequest{
			FromVersion: req.FromVersion, ToVersion: req.ToVersion,
			FromEdition: req.FromEdition, ToEdition: req.ToEdition,
			Data: req.Item,
		}

		cInput, err := prepareRequest(nbtReq)
		if err != nil {
			return nil, err
		}
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_item(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_item returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		var responseItem Item
		if err := processResponse(cResult, &responseItem); err != nil {
			return nil, err
		}
		return &responseItem, nil
	})

	if err != nil {
		return nil, err
	}
	return res.(*Item), nil
}

// ConvertEntity converts an entity between editions.
func (c *Converter) ConvertEntity(req EntityRequest) (*Entity, error) {
	res, err := c.dispatch(req, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		req := payload.(EntityRequest)
		nbtReq := nbtRequest{
			FromVersion: req.FromVersion, ToVersion: req.ToVersion,
			FromEdition: req.FromEdition, ToEdition: req.ToEdition,
			Data: req.Entity,
		}

		cInput, err := prepareRequest(nbtReq)
		if err != nil {
			return nil, err
		}
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_entity(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_entity returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		var responseEntity Entity
		if err := processResponse(cResult, &responseEntity); err != nil {
			return nil, err
		}
		return &responseEntity, nil
	})

	if err != nil {
		return nil, err
	}
	return res.(*Entity), nil
}

// ConvertBiome converts a biome identifier between editions.
func (c *Converter) ConvertBiome(req BiomeRequest) (*BiomeResponse, error) {
	res, err := c.dispatch(req, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		req := payload.(BiomeRequest)
		nbtReq := nbtRequest{
			FromVersion: req.FromVersion, ToVersion: req.ToVersion,
			FromEdition: req.FromEdition, ToEdition: req.ToEdition,
			Data: req.Data,
		}

		cInput, err := prepareRequest(nbtReq)
		if err != nil {
			return nil, err
		}
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_biome(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_biome returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		var responseBiome BiomeResponse
		if err := processResponse(cResult, &responseBiome); err != nil {
			return nil, err
		}
		return &responseBiome, nil
	})

	if err != nil {
		return nil, err
	}
	return res.(*BiomeResponse), nil
}

// ConvertBlockEntity converts a block entity between editions.
func (c *Converter) ConvertBlockEntity(req BlockEntityRequest) (*BlockEntity, error) {
	res, err := c.dispatch(req, func(thread *C.graal_isolatethread_t, payload any) (any, error) {
		req := payload.(BlockEntityRequest)
		nbtReq := nbtRequest{
			FromVersion: req.FromVersion, ToVersion: req.ToVersion,
			FromEdition: req.FromEdition, ToEdition: req.ToEdition,
			Data: req.BlockEntity,
		}

		cInput, err := prepareRequest(nbtReq)
		if err != nil {
			return nil, err
		}
		defer C.free(unsafe.Pointer(cInput))

		cResult := C.convert_block_entity(thread, cInput)
		if cResult == nil {
			return nil, fmt.Errorf("cgo call to convert_block_entity returned a null pointer")
		}
		defer C.free_result(thread, cResult)

		var responseBlockEntity BlockEntity
		if err := processResponse(cResult, &responseBlockEntity); err != nil {
			return nil, err
		}
		return &responseBlockEntity, nil
	})

	if err != nil {
		return nil, err
	}
	return res.(*BlockEntity), nil
}
