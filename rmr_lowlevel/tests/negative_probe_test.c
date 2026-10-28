#include "rmr_ime.h"
#define T(x) do { if (!(x)) return __LINE__; } while (0)
int main(void) {
    rmr_ime_state s = {0};
    rmr_ime_event e = {0};
    rmr_ime_decision d = {0};
    uint8_t input[16] = {0};
    uint32_t ascii = 999u, backend = 999u;
    T(rmr_ime_ascii_probe(input, RMR_PROBE_MAX_BYTES + 1u, 0u, &ascii, &backend) == RMR_REJECT);
    T(rmr_ime_ascii_probe(input, UINT32_MAX, RMR_CAP_NEON, &ascii, &backend) == RMR_REJECT);
    T(rmr_ime_ascii_probe(input, 16u, 0u, &ascii, &backend) == RMR_OK && ascii == 1u);
    rmr_ime_reset(&s, 7u, 0u);
    e.epoch = 7u; e.sequence = 1u; e.opcode = RMR_OP_TEXT_META; e.payload_len = 0u;
    T(rmr_ime_route(&s, &e, &d) == RMR_OK);
    s.sequence = UINT32_MAX;
    e.sequence = 0u;
    T(rmr_ime_route(&s, &e, &d) == RMR_REJECT);
    rmr_ime_reset(&s, 8u, 0u);
    e.epoch = 8u; e.sequence = 1u; e.opcode = RMR_OP_DELETE; e.payload_len = 1u;
    T(rmr_ime_route(&s, &e, &d) == RMR_FALLBACK && d.backend == RMR_BACKEND_PLATFORM);
    return 0;
}
