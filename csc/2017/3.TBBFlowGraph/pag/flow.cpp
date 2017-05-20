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

image imread(const std::string &path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        cerr << "Can read only prepared .dat files!" << endl;
        throw invalid_argument(path);
    }

    ifstream file(path, ios::binary | ios::in);

    std::uint32_t h, w, d;
    file.read(reinterpret_cast<char *>(&h), 4);
    file.read(reinterpret_cast<char *>(&w), 4);
    file.read(reinterpret_cast<char *>(&d), 4);

    auto data = vector<vector<pixel>>(h);
    for (auto &row: data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = pixel {uint8_t(pix[0]),
                                uint8_t(pix[1]),
                                uint8_t(pix[2])};
        }
    }

    return data;
}

void imwrite(const image &source, const string &path) {
    int h = source.size();
    int w = source[0].size();
    int d = 3;
    ofstream file(path, ios::binary);
    file.write(reinterpret_cast<char *>(&h), 4);
    file.write(reinterpret_cast<char *>(&w), 4);
    file.write(reinterpret_cast<char *>(&d), 4);

    for (auto &row : source) {
        for (auto &pix: row) {
            file.write(reinterpret_cast<const char *>(&pix.r), 1);
            file.write(reinterpret_cast<const char *>(&pix.g), 1);
            file.write(reinterpret_cast<const char *>(&pix.b), 1);
        }
    }
    file.close();
}


class ImgSourceBody {
    image img;
public:
    ImgSourceBody(const image &_img) {
        img = _img;
    }

    bool operator()(image &result) {
        result = img;
//        cout << "img:" << result.size() << endl;
        return true;
    }
};

image nullImage;

struct Region {
    int x1 = 0;
    int y1 = 0;
    int x2 = 0;
    int y2 = 0;
    const image *bigImg;
    const image *smallImg;

    Region() : x1(0), y1(0), x2(0), y2(0), bigImg(nullptr), smallImg(nullptr) {}

    Region(int _x1, int _y1, int _x2, int _y2, const image *_bigImg, const image *_smallImg) :
            bigImg(_bigImg), smallImg(_smallImg) {
        x1 = _x1;
        y1 = _y1;
        x2 = _x2;
        y2 = _y2;
    }
};

class RegionSourceBody {
    vector<Region> regions;
    unsigned long size;
    unsigned long i = 0;
public:
    RegionSourceBody(const image &bigImg, const image &smallImg) {
        auto hb = bigImg.size();
        auto wb = bigImg.front().size();
        auto hs = smallImg.size();
        auto ws = smallImg.front().size();
        cout << "region big(" << hb << "," << wb << ") small(" << hs << "," << ws << ")" << endl;
        auto h = hb - hs;
        auto w = wb - ws;
        for (auto i = 0; i < h; ++i) {
            for (auto j = 0; j < w; ++j) {
                Region region(i, j, i + hs - 1, j + ws - 1, &bigImg, &smallImg);
                regions.push_back(region);
            }
        }
        size = regions.size();
        cout << "Amount of tasks: " << size << endl;
    }

    bool operator()(Region &result) {
        if (i < size) {
//            cout << "send:" << i << endl;
            result = regions[i];
            i++;
            return true;
        }
        return false;
    }
};

struct CalcDiffBody {
    tuple<Region, long> operator()(const Region region) {
        long acc = 0;
        auto h = region.smallImg->size();
        auto w = region.smallImg->front().size();
        for (auto i = 0; i < h; ++i) {
            for (auto j = 0; j < w; ++j) {
                const pixel &p1 = (*region.bigImg)[region.x1 + i][region.y1 + j];
                const pixel &p2 = (*region.smallImg)[i][j];
                double dif = abs(p1.b - p2.b) + abs(p1.g - p2.g) + abs(p1.r - p2.r);
                acc += dif;
            }
        }
        return std::make_tuple(region, acc);
    }
};

class MinSincBody {
    long &result;
    Region &finalRegion;
public:

    MinSincBody(Region &_finalResult, long &_result) : finalRegion(_finalResult), result(_result) {
//        result =
    }

    void operator()(tuple<Region, long> data) {
        long val = get<1>(data);
        if (result > val) {
            result = val;
            finalRegion = get<0>(data);
        }
    }

};

void handle(const string &pathToBig, const string &pathToSmall, const string &pathToOut) {
    graph g;
    const image &mainImg = imread(pathToBig);
    const image &smallImg = imread(pathToSmall);

    source_node<Region> regionSource(g, RegionSourceBody(mainImg, smallImg), false);
    buffer_node<Region> preCalcBuffer(g);
    function_node<Region, tuple<Region, long>> calcDiff(g, unlimited, CalcDiffBody());

    Region region;
    long result = numeric_limits<long>::max();
    function_node<tuple<Region, long>> minSinc(g, 1, MinSincBody(region, result));

    make_edge(regionSource, preCalcBuffer);
    make_edge(preCalcBuffer, calcDiff);
    make_edge(calcDiff, minSinc);

    regionSource.activate();
    g.wait_for_all();


    cout << "Found diff:" << result << "(" << region.x1 << "," << region.y1 << ")"
         << "(" << region.x2 << "," << region.y2 << ")" << endl;
    int h = region.x2 - region.x1;
    int w = region.y2 - region.y1;
    auto data = vector<vector<pixel>>(h);
    for (auto &row: data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = array<char, 3>();
            data[i][j] = (*region.bigImg)[i + region.x1][j + region.y1];
        }
    }
    imwrite(data, pathToOut);
}

int main(int argc, char *argv[]) {
    handle("data/image.dat", "data/chicken.dat", "data/chicken_out.dat");
    handle("data/image.dat", "data/hat.dat", "data/hat_out.dat");
    handle("data/image.dat", "data/cheer.dat", "data/cheer_out.dat");
    return 0;
}