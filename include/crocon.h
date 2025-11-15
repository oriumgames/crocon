#ifndef __LIBCROCON_H
#define __LIBCROCON_H

#include "graal_isolate.h"

/*
 * All conversion functions accept a null-terminated C string containing
 * Base64-encoded NBT data.
 *
 * They return a pointer to a newly allocated, null-terminated C string
 * containing the result. This result string MUST be freed by the caller
 * by passing the pointer to the free_result() function to avoid memory leaks.
 */
char* convert_block(graal_isolatethread_t*, char*);
char* convert_item(graal_isolatethread_t*, char*);
char* convert_biome(graal_isolatethread_t*, char*);
char* convert_entity(graal_isolatethread_t*, char*);
char* convert_block_entity(graal_isolatethread_t*, char*);

/**
 * Frees the memory for a result pointer that was returned by one of
 * the convert_* functions.
 */
void free_result(graal_isolatethread_t*, char*);
#endif
