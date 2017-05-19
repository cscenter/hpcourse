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

void findImg(const string& imgPath, const string& patternPath, const string& outputPath) {
    graph g;

    image img = imread(imgPath);
    image pattern = imread(patternPath);

    size_t img_h = img.size();
    size_t img_w = img[0].size();

    size_t h = pattern.size();
    size_t w = pattern[0].size();

    using slice = tuple<size_t, size_t>;
    using slicer_node = source_node<slice>;
    using slicer_succ = slicer_node::successor_type;

    size_t x = 0;
    size_t y = 0;

    slicer_node slicer(g, [img_h, img_w, h, w, &x, &y, &img, &pattern] (slice& s) {
        if (y + h == img_h) {
            return false;
        } else if (x + w == img_w) {
            x = 0;
            ++y;
        }

        s = tie(x, y);
        ++x;
        return true;
    }, false);

    buffer_node<slice> buf(g);

    make_edge(slicer, buf);

    using similarity = tuple<size_t, size_t, size_t>;
    using comparator_node = function_node<slice, similarity>;

     comparator_node comparator(g, unlimited, [h, w, &pattern, &img] (slice s) {
        size_t x, y;
        tie(x, y) = s;

        size_t dist = 0;
        for (size_t i = 0; i < h; i++) {
            for (size_t j = 0; j < w; ++j) {
                dist += diff(pattern[i][j], img[y + i][x + j]);
            }
        }

        return make_tuple(x, y, dist);
    });

    make_edge(buf, comparator);

    size_t min_dist = numeric_limits<size_t>::max();
    size_t min_x = 0;
    size_t min_y = 0;

    using reducer_node = function_node<similarity, size_t>;

    reducer_node reducer(g, unlimited, [&min_dist, &min_x, &min_y] (similarity s) {
        size_t x, y, dist;
        tie(x, y, dist) = s;
        if (dist < min_dist) {
            min_dist = dist;
            min_x = x;
            min_y = y;
        }
        return min_dist;
    });

    make_edge(comparator, reducer);

    slicer.activate();
    g.wait_for_all();

    image output = allocate_image(h, w);
    for (size_t i = 0; i < h; ++i) {
        for (size_t j = 0; j < w; ++j) {
            output[i][j] = img[min_y + i][min_x + j];
        }
    }

    imwrite(output, outputPath);

    cerr << "Best match " << min_dist << " at x=" << min_x << ", y=" << min_y << endl;
}

int main() {
    findImg("data/image.dat", "data/cheer.dat", "data/cheer_found.dat");
    findImg("data/image.dat", "data/chicken.dat", "data/chicken_found.dat");
    findImg("data/image.dat", "data/hat.dat", "data/hat_found.dat");
}