#ifndef FMO_PROCESSING_HPP
#define FMO_PROCESSING_HPP

#include <fmo/image.hpp>

namespace fmo {
    /// Saves an image to file.
    void save(const Mat& src, const std::string& filename);

    /// Copies image data. To accomodate the data from "src", resize() is called on "dst".
    void copy(const Mat& src, Mat& dst);

    /// Copies image data. To accomodate the data from "src", resize() is called on "dst".
    /// Regardless of the source format, the destination format is set to "format". Color
    /// conversions performed by this function are not guaranteed to make any sense.
    void copy(const Mat& src, Mat& dst, Format format);

    /// Converts the image "src" to a given color format and saves the result to "dst". One could
    /// pass the same object as both "src" and "dst", but doing so is ineffective, unless the
    /// conversion is YUV420SP to GRAY. Only some conversions are supported, namely: GRAY to BGR,
    /// BGR to GRAY, YUV420SP to BGR, YUV420SP to GRAY.
    void convert(const Mat& src, Mat& dst, Format format);

    /// Selects pixels that have a value less than the specified value; these are set to 0xFF while
    /// others are set to 0x00. Input image must be GRAY.
    void less_than(const Mat& src1, Mat& dst, uint8_t value);

    /// Selects pixels that have a value greater than the specified value; these are set to 0xFF
    /// while others are set to 0x00. Input image must be GRAY.
    void greater_than(const Mat& src1, Mat& dst, uint8_t value);

    /// Calculates the absolute difference between the two images. Input images must have the same
    /// format and size.
    void absdiff(const Mat& src1, const Mat& src2, Mat& dst);

    /// Resizes an image so that each dimension is divided by two.
    void subsample(const Mat& src, Mat& dst);

    /// Calculates the per-pixel median of three images.
    void median3(const Image& src1, const Image& src2, const Image& src3, Image& dst);
}

#endif // FMO_PROCESSING_HPP
