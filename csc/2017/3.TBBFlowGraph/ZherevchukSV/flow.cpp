#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <string>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

using image = vector<vector<pixel>>;

struct imageSegment {
    uint32_t x1, y1, x2, y2 = 0;

    imageSegment() {}

    imageSegment(std::uint32_t x1_, std::uint32_t y1_, std::uint32_t width, std::uint32_t height) {
        x1 = x1_;
        y1 = y1_;
        x2 = x1 + width;
        y2 = y1 + height;
    }
};

image imread(const std::string& path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        cerr << "Can read only prepared .dat files!" << endl;
        throw invalid_argument(path);
    }

    ifstream file(path, ios::binary | ios::in);

    if (!file.is_open()) {
        cerr << "Can not open the file" << endl;
        throw invalid_argument(path);
    }

    std::uint32_t h, w, d;
    file.read(reinterpret_cast<char*>(&h), 4);
    file.read(reinterpret_cast<char*>(&w), 4);
    file.read(reinterpret_cast<char*>(&d), 4);

    auto data = vector<vector<pixel>>(h);
    for (auto& row: data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = pixel { uint8_t(pix[0]),
                                 uint8_t(pix[1]),
                                 uint8_t(pix[2])};
        }
    }

    return data;
}

void imwrite(const image& source, const string& path) {
    int h = source.size();
    int w = source[0].size();
    int d = 3;

    ofstream file(path, ios::binary);

    if (!file.is_open()) {
        cerr << "Can not open the file" << endl;
        throw invalid_argument(path);
    }

    file.write(reinterpret_cast<char*>(&h), 4);
    file.write(reinterpret_cast<char*>(&w), 4);
    file.write(reinterpret_cast<char*>(&d), 4);

    for (auto& row : source) {
        for (auto& pix: row) {
            file.write(reinterpret_cast<const char*>(&pix.r), 1);
            file.write(reinterpret_cast<const char*>(&pix.g), 1);
            file.write(reinterpret_cast<const char*>(&pix.b), 1);
        }
    }
    file.close();
}


const image image_original = imread("image.dat");
imageSegment min_segment;


// FIXME: deadly inefficient by memory
class SegmentsGenerator {
    std::vector<imageSegment> segments;
public:
    SegmentsGenerator(const image& image_segment) {
        const auto segment_height = image_segment.size();
        const auto segment_width = image_segment.front().size();
        for (auto y = 0; y < image_original.size() - segment_height; ++y) {
            for (auto x = 0; x < image_original.at(0).size() - segment_width; ++x) {
                segments.push_back(imageSegment(x, y, segment_width, segment_height));
            }
        }
    }

    bool operator()(imageSegment &result) {
        if (segments.empty()) {
            return false;
        }

        result = segments.back();
        segments.pop_back();

        return true;
    }
};


class SegmentsDiff {
    image orig_segment_image;
public:
    SegmentsDiff(const image& seg) : orig_segment_image(seg) { }

    std::tuple<imageSegment, int64_t> operator()(const imageSegment &segment) {
        uint64_t difference = 0;
        for (auto x = segment.x1; x < segment.x2; ++x) {
            for (auto y = segment.y1; y < segment.y2; ++y) {
                // XXX: in `image` structure first argument - y coord
                pixel p1 = image_original[y][x];
                pixel p2 = orig_segment_image[y - segment.y1][x - segment.x1];
                difference += abs(p1.b - p2.b) + abs(p2.g - p1.g) + abs(p1.r - p2.r);
            }
        }
        return std::make_tuple(segment, difference);
    }
};


class MinDiffSeeker {
    int64_t min_diff = std::numeric_limits<int64_t>::max();
public:
    std::tuple<imageSegment, int64_t> operator()(std::tuple<imageSegment, int64_t> const &next) {
        if (std::get<1>(next) < min_diff) {
            min_diff = std::get<1>(next);
            min_segment = std::get<0>(next);
        }
        return min_segment;
    }
};


image segment_to_image(const imageSegment &segment) {
    int width = segment.x2 - segment.x1 + 1;
    int height = segment.y2 - segment.y1 + 1;

    image result;
    result.resize(height);
    for (vector<pixel> &row : result) {
        row.resize(width);
    }

    for (int x = 0; x < width; ++x) {
        for (int y = 0; y < height; ++y) {
            result[y][x] = image_original[y + segment.y1][x + segment.x1];
        }
    }

    return result;
}


void process_image(std::string image_segment_filename) {
    auto image_segment = imread(image_segment_filename + ".dat");

    graph g;

    source_node<imageSegment> segments_generator(g, SegmentsGenerator(image_segment), false);

    buffer_node<imageSegment> segments_buffer(g);

    function_node<imageSegment, std::tuple<imageSegment, int64_t> > segments_diff(g, unlimited, SegmentsDiff(image_segment));

    function_node<std::tuple<imageSegment, int64_t>> min_diff_node(g, serial, MinDiffSeeker());

    make_edge(segments_generator, segments_buffer);
    make_edge(segments_buffer, segments_diff);
    make_edge(segments_diff, min_diff_node);

    segments_generator.activate();
    g.wait_for_all();

    std::cout << image_segment_filename << " base point on original image (" << min_segment.x1 << ", " << min_segment.y1 << ")" << std::endl;
    imwrite(segment_to_image(min_segment), image_segment_filename + "_found.dat");

}


int main() {
    process_image("cheer");
    process_image("chicken");
    process_image("hat");

    return 0;
}