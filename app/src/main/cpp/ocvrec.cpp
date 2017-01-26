#include "ocvrec.hpp"

void ocvRecStart(const Callback &cb) {
    cb.frameTimings(1, 2, 3);
}

void ocvRecStop() { }
