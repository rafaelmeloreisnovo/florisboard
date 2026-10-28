#include "rmr_ime.h"
#define T(x) do { if (!(x)) return __LINE__; } while (0)
int main(void) {
    rmr_ime_state s = {0};
    rmr_ime_event e = {0};
    rmr_ime_decision d = {0};
    uint32_t a = 77u, backend = 77u, i;
    uint8_t utf8[33] = {0};
    T(rmr_ime_route(&s, &e, &d) == RMR_REJECT);
    T(rmr_ime_route(&s, &e, 0) == RMR_REJECT);
    rmr_ime_reset(&s, 9u, 0u);
    e.epoch = 9u; e.sequence = 1u; e.opcode = RMR_OP_TEXT_META; e.payload_len = 3u;
    T(rmr_ime_route(&s, &e, &d) == RMR_OK && d.backend == RMR_BACKEND_SCALAR);
    T(rmr_ime_route(&s, &e, &d) == RMR_REJECT); /* Replay */
    e.sequence = 2u; e.epoch = 8u;
    T(rmr_ime_route(&s, &e, &d) == RMR_REJECT); /* Stale epoch */
    e.epoch = 9u; e.payload_len = 129u;
    T(rmr_ime_route(&s, &e, &d) == RMR_FALLBACK && d.backend == RMR_BACKEND_PLATFORM);
    e.sequence = 3u; e.opcode = 99u; e.payload_len = 0u;
    T(rmr_ime_route(&s, &e, &d) == RMR_FALLBACK);
    e.sequence = 4u; T(rmr_ime_route(&s, &e, &d) == RMR_FALLBACK);
    e.sequence = 5u; T(rmr_ime_route(&s, &e, &d) == RMR_WATCHDOG);
    rmr_ime_reset(&s, 10u, RMR_FLAG_SENSITIVE_FIELD);
    e.epoch = 10u; e.sequence = 1u; e.opcode = RMR_OP_TEXT_META;
    T(rmr_ime_route(&s, &e, &d) == RMR_FALLBACK);
    rmr_ime_reset(&s, 11u, 0u);
    e.epoch = 11u; e.opcode = RMR_OP_DELETE; e.payload_len = 0u;
    for (i = 1u; i <= RMR_EVENT_BUDGET; ++i) {
        e.sequence = i;
        T(rmr_ime_route(&s, &e, &d) == RMR_OK);
    }
    e.sequence = RMR_EVENT_BUDGET + 1u;
    T(rmr_ime_route(&s, &e, &d) == RMR_WATCHDOG);
    T(rmr_ime_ascii_probe(0, 0u, 0u, &a, &backend) == RMR_OK && a == 1u);
    T(rmr_ime_ascii_probe(0, 1u, 0u, &a, &backend) == RMR_REJECT);
    T(rmr_ime_ascii_probe(utf8, 33u, RMR_CAP_NEON, &a, &backend) == RMR_OK && a == 1u);
    utf8[0] = 0xC3u; utf8[32] = 0xA9u;
    T(rmr_ime_ascii_probe(utf8, 33u, RMR_CAP_NEON, &a, &backend) == RMR_OK && a == 0u);
    T(rmr_ime_ascii_probe(utf8, 33u, 0u, &a, &backend) == RMR_OK && a == 0u && backend == RMR_BACKEND_SCALAR);
    return 0;
}
