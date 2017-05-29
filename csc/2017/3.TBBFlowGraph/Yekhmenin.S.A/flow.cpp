#include <iostream>
#include <fstream>
#include <algorithm>
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

using image = vector<vector<pixel>>;

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

struct Frame {
    size_t x1 = 0, y1 = 0, x2 = 0, y2 = 0;

    Frame() {}

    Frame(size_t x1, size_t y1, size_t x2, size_t y2) : x1(x1), y1(y1), x2(x2), y2(y2) {}
};

class Slice {
    vector<Frame> frames;

public:
    Slice(const image &pattern, const image &img) {
        int imgWidth = img.size();
        int imgHeight = img[0].size();
        int patternWidth = pattern.size();
        int patternHeight = pattern[0].size();

        for (int i = 0; i < imgWidth - patternWidth; ++i) {
            for (int j = 0; j < imgHeight - patternHeight; ++j) {
                frames.push_back(Frame(i, j, i + patternWidth, j + patternHeight));
            }
        }
    }

    bool operator()(Frame &result) {
        if (frames.empty())
            return false;
        result = frames.back();
        frames.pop_back();
        return true;
    }
};

class GetDiff {
    image img, pattern;

    int pixelDiff(const pixel &p1, const pixel &p2) {
        return (int) abs(p1.r - p2.r) + abs(p1.b - p2.b) + abs(p1.g - p2.g);
    }

public:
    GetDiff(const image &pattern, const image &img) : img(img), pattern(pattern) {}

    tuple<Frame, long long> operator() (const Frame& frame) {
        long long diff = 0;
        size_t height = pattern.size();
        size_t width = pattern[0].size();

        for (size_t i = 0; i < height; ++i) {
            for (size_t j = 0; j < width; ++j) {
                diff += pixelDiff(pattern[i][j], img[frame.x1 + i][frame.y1 + j]);
            }
        }

        return make_tuple(frame, diff);
    }
};

class FindMinDiff {
    int min = INT_MAX;
    Frame& result;
public:
    FindMinDiff (Frame& result) : result(result) {}

    Frame operator()(const tuple<Frame, long long> &args) {
        const Frame &frame = get<0>(args);
        long long diff = get<1>(args);

        if (diff < min) {
            min = diff;
            result = frame;
        }

        return result;
    }
};

void find(const string &imgPath, const string &patternPath, const string &resPath) {
    graph g;

    image img = imread(imgPath);
    image pattern = imread(patternPath);

    Frame frame;

    source_node<Frame> slice(g, Slice(pattern, img), false);
    buffer_node<Frame> sliceBuffer(g);
    function_node<Frame, tuple<Frame, long long>> getDiff(g, unlimited, GetDiff(pattern, img));
    function_node<tuple<Frame, long long>, Frame> findMinDiff(g, serial, FindMinDiff(frame));

    make_edge(slice, sliceBuffer);
    make_edge(sliceBuffer, getDiff);
    make_edge(getDiff, findMinDiff);
    slice.activate();
    g.wait_for_all();

    size_t h = frame.x2 - frame.x1;
    size_t w = frame.y2 - frame.y1;
    image result = vector<vector<pixel>>(h);

    for (auto &line : result) {
        line = vector<pixel>(w);
    }

    for (size_t i = 0; i < h; ++i) {
        for (size_t j = 0; j < w; ++j) {
            result[i][j] = img[i + frame.x1][j + frame.y1];
        }
    }

    cout << "Result for " << patternPath << ": " << "x = " << frame.x1 << ", y = " << frame.y1 << endl;
    imwrite(result, resPath);
}

int main() {
    const string imgPath = "data/image.dat";

    find(imgPath, "data/cheer.dat", "data/cheer_res.dat");
    find(imgPath, "data/hat.dat", "data/hat_res.dat");
    find(imgPath, "data/chicken.dat", "data/chicken_res.dat");

    return 0;
}
