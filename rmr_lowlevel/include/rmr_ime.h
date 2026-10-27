/* RAFAELIA experimental original shim. No claim over FlorisBoard upstream code. */
#ifndef RMR_IME_H
#define RMR_IME_H
#include <stdint.h>
#define RMR_IME_ABI 1u
#define RMR_CAP_NEON 1u
#define RMR_FLAG_SENSITIVE_FIELD 1u
#define RMR_OP_TEXT_META 1u
#define RMR_OP_DELETE 2u
#define RMR_OP_ACTION 3u
#define RMR_OK 0u
#define RMR_FALLBACK 1u
#define RMR_REJECT 2u
#define RMR_WATCHDOG 3u
#define RMR_BACKEND_PLATFORM 0u
#define RMR_BACKEND_SCALAR 1u
#define RMR_BACKEND_NEON 2u
#define RMR_EVENT_BUDGET 4096u
#define RMR_FAULT_BUDGET 3u

typedef struct {
    uint32_t abi;
    uint32_t epoch;
    uint32_t sequence;
    uint32_t events;
    uint32_t faults;
    uint32_t flags;
} rmr_ime_state;
typedef struct {
    uint32_t epoch;
    uint32_t sequence;
    uint32_t opcode;
    uint32_t payload_len; /* Metadata only: no keystrokes or text retained. */
} rmr_ime_event;
typedef struct {
    uint32_t status;
    uint32_t backend;
} rmr_ime_decision;

void rmr_ime_reset(rmr_ime_state *s, uint32_t epoch, uint32_t flags);
uint32_t rmr_ime_route(rmr_ime_state *s, const rmr_ime_event *e, rmr_ime_decision *d);
uint32_t rmr_ime_ascii_probe(const uint8_t *bytes, uint32_t n,
                              uint32_t validated_capabilities, uint32_t *ascii,
                              uint32_t *backend);
#endif
