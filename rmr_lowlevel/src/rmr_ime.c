/* Freestanding, deterministic, fixed-size C11 core. Experimental and opt-in. */
#include "rmr_ime.h"
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define RMR_COMPILED_NEON 1
#else
#define RMR_COMPILED_NEON 0
#endif

void rmr_ime_reset(rmr_ime_state *s, uint32_t epoch, uint32_t flags) {
    if (!s) return;
    s->abi = RMR_IME_ABI;
    s->epoch = epoch;
    s->sequence = 0u;
    s->events = 0u;
    s->faults = 0u;
    s->flags = flags & RMR_FLAG_SENSITIVE_FIELD;
}

uint32_t rmr_ime_route(rmr_ime_state *s, const rmr_ime_event *e, rmr_ime_decision *d) {
    if (!d) return RMR_REJECT;
    d->status = RMR_REJECT;
    d->backend = RMR_BACKEND_PLATFORM;
    if (!s || !e || s->abi != RMR_IME_ABI) return d->status;
    if (e->epoch != s->epoch || s->sequence == UINT32_MAX ||
        e->sequence != s->sequence + 1u) return d->status;
    /* These are synchronous budget guards, NOT real-time background watchdogs. */
    if (s->events >= RMR_EVENT_BUDGET || s->faults >= RMR_FAULT_BUDGET) {
        d->status = RMR_WATCHDOG;
        return d->status;
    }
    s->sequence = e->sequence;
    ++s->events;
    if ((s->flags & RMR_FLAG_SENSITIVE_FIELD) != 0u ||
        (e->opcode != RMR_OP_TEXT_META && e->opcode != RMR_OP_DELETE &&
         e->opcode != RMR_OP_ACTION) ||
        (e->opcode == RMR_OP_TEXT_META && e->payload_len > 128u) ||
        (e->opcode != RMR_OP_TEXT_META && e->payload_len != 0u)) {
        ++s->faults;
        d->status = RMR_FALLBACK;
        return d->status;
    }
    s->faults = 0u;
    d->status = RMR_OK;
    d->backend = RMR_BACKEND_SCALAR;
    return d->status;
}

uint32_t rmr_ime_ascii_probe(const uint8_t *bytes, uint32_t n,
                              uint32_t validated_capabilities, uint32_t *ascii,
                              uint32_t *backend) {
    if (!ascii || !backend || n > RMR_PROBE_MAX_BYTES || (n != 0u && !bytes)) return RMR_REJECT;
    *ascii = 1u;
    *backend = RMR_BACKEND_SCALAR;
    uint32_t i = 0u;
#if RMR_COMPILED_NEON
    if ((validated_capabilities & RMR_CAP_NEON) != 0u && n >= 16u) {
        uint8x16_t high = vdupq_n_u8(0x80u);
        uint8x16_t combined = vdupq_n_u8(0u);
        for (; i <= n - 16u; i += 16u) {
            combined = vorrq_u8(combined, vandq_u8(vld1q_u8(bytes + i), high));
        }
        uint8_t lanes[16];
        vst1q_u8(lanes, combined);
        uint32_t j;
        for (j = 0u; j < 16u; ++j) if (lanes[j]) *ascii = 0u;
        *backend = RMR_BACKEND_NEON;
    }
#else
    (void)validated_capabilities;
#endif
    for (; i < n; ++i) if (bytes[i] & 0x80u) *ascii = 0u;
    return RMR_OK;
}
