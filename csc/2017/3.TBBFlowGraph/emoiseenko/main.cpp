#include <iostream>
#include <fstream>
#include <algorithm>
#include <string>
#include <array>
#include <cmath>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

size_t diff(pixel &a, pixel &b) {
    return abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b);
}

using image = vector<vector<pixel>>;

image allocate_image(size_t h, size_t w) {
    image img(h);
    for (auto& row: img) {
        row.resize(w);
    }
    return img;
}

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

class slicer {
public:
    slicer(const std::string& path)
        : img(imread(path))
    {}

    using succ_node = function_node<image, tuple<size_t, size_t, image, image>>::successor_type;

    void operator()(image pattern, succ_node& succ) const {
        size_t h = pattern.size();
        size_t w = pattern[0].size();

        size_t img_h = img.size();
        size_t img_w = img[0].size();

        for (size_t x = 0; x + h < img_h; ++x) {
            for (size_t y = 0; y + w < img_w; ++y) {
                image frame = allocate_image(h, w);
                for (size_t i = 0; i < h; ++i) {
                    for (size_t j = 0; j < w; ++j) {
                        frame[i][j] = img[x + i][y + j];
                    }
                }
                succ.try_put(make_tuple(x, y, frame, pattern));
            }
        }
    }
private:
    image img;
};

void findImg(const string& patternPath, const string& imgPath) {
    graph g;

    image img;
    image pattern;

    size_t h = pattern.size();
    size_t w = pattern[0].size();

    typedef multifunction_node<string, image> imread_node;

    multifunction_node read_pattern(g, unlimited,
        [] (const std::string& path, imread_node::output_ports_type &op) {
        image img = imread(path);
        get<0>(op).try_put(img);
    });

    function_node slicer_node(g, unlimited, slicer(imgPath));

    using cmp_info = tuple<size_t, size_t, image>;

    function_node<cmp_info, size_t> comparator(g, unlimited, [h, w, &pattern] (cmp_info info) {
        size_t x, y;
        image frame;
        tie(x, y, frame) = info;

        size_t dist = 0;
        for (size_t i = 0; i < h; i++) {
            for (size_t j = 0; j < w; ++j) {
                dist += diff(pattern[i][j], img[x + i][y + j]);
            }
        }

        return dist;
    });
}

int main() {
    return 0;
}