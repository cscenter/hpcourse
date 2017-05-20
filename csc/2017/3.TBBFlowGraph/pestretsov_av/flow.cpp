#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

#include <tbb/flow_graph.h>

#define INF std::numeric_limits<int64_t>::max();

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

class Region {
public:
    size_t x, dx, y, dy;

    Region() {}

    Region(size_t x, size_t dx, size_t y, size_t dy) : x(x), dx(dx), y(y), dy(dy) {}
};

class Splitter {
public:
    Splitter(const image& pattern, const image& bigImg) {
        size_t patternHeight = pattern.size();
        size_t patternWidth = pattern.front().size();

        size_t imageHeight = bigImg.size();
        size_t imageWidth = bigImg.front().size();

        for (size_t y = 0; y < imageHeight - patternHeight; ++y) {
            for (size_t x = 0; x < imageWidth - patternWidth; ++x) {
                regions.push_back(Region(x, x + patternWidth - 1, y, y + patternHeight - 1));
            }
        }
    }

    bool operator()(Region& reg) {
        if (regions.empty()) {
            return false;
        }
        reg = regions.back();
        regions.pop_back();
        return true;
    }

private:
    vector<Region> regions;
};

int calcDiff(const pixel& fst, const pixel& snd) {
    return abs(fst.r - snd.r) + abs(fst.g - snd.g) + abs(fst.b - snd.b);
}

void regionToImage(const Region& reg, const image& bigImg, const string& foundImgName) {
    image outputImg;

    for (size_t y = 0; y <= reg.dy - reg.y; ++y) {
        vector<pixel> pixelLine;
        for (size_t x = 0; x <= reg.dx - reg.x; ++x) {
            pixelLine.push_back(bigImg[y+reg.y][x+reg.x]);
        }
        outputImg.push_back(pixelLine);
    }

    imwrite(outputImg, foundImgName);
}

void findImage(const string& patternPath, const string& bigImgPath, const string& foundImgName) {
    graph g;

    image pattern = imread(patternPath);
    image bigImg = imread(bigImgPath);

    size_t patternHeight = pattern.size();
    size_t patternWidth = pattern.front().size();

    source_node<Region> cutImageNode(g, Splitter(pattern, bigImg), true);
    buffer_node<Region> regionsBufferNode(g);

    function_node<Region, pair<int64_t, Region>> calcDiffNode(g, unlimited,
        [&](const Region& reg) -> pair<int64_t, Region> {
            int64_t diff = 0;

            for (size_t y = 0; y < patternHeight; ++y) {
                for (size_t x = 0; x < patternWidth; ++x) {
                    diff += calcDiff(bigImg[reg.y + y][reg.x + x], pattern[y][x]);
                }
            }

            return {diff, reg};
        }
    );

    buffer_node<pair<int64_t, Region>> diffBufferNode(g);

    int64_t min = INF;
    Region result;
    function_node<pair<int64_t, Region>> findMinNode(g, 1,
        [&](pair<int64_t, Region> p) -> void {
            if (p.first < min) {
                result = p.second;
                min = p.first;
            }
        }
    );

    make_edge(cutImageNode, regionsBufferNode);
    make_edge(regionsBufferNode, calcDiffNode);
    make_edge(calcDiffNode, diffBufferNode);
    make_edge(diffBufferNode, findMinNode);

    g.wait_for_all();

    cout << "Top left corner coords:\n";
    cout << "(" << result.x << ", " << result.y << ")" << endl;

    regionToImage(result, bigImg, foundImgName);
}

int main() {

    findImage("../data/cheer.dat", "../data/image.dat", "../data/cheer_res.dat");
    findImage("../data/chicken.dat", "../data/image.dat", "../data/chicken_res.dat");
    findImage("../data/hat.dat", "../data/image.dat", "../data/hat_res.dat");

    return 0;
}
