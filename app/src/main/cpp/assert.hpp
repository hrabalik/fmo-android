#ifndef FMO_ASSERT_HPP
#define FMO_ASSERT_HPP

namespace fmo {
    void assertFail(const char *what, const char *file, int line);
}

#define FMO_ASSERT(expr, reason) if (!(expr)) { fmo::assertFail(reason, __FILE__, __LINE__); }

#endif //FMO_ASSERT_HPP
