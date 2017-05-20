#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <functional>
#include <memory>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

static const std::string DATA_PATH = "../data/";

static const std::string SOURCE_PATH = DATA_PATH + "image.dat";

static const std::string CHEER_PATH = DATA_PATH + "cheer.dat";
static const std::string CHEER_RESULT_PATH   = DATA_PATH + "cheer_res.dat";

static const std::string CHICKEN_PATH = DATA_PATH + "chicken.dat";
static const std::string CHICKEN_RESULT_PATH = DATA_PATH + "chicken_res.dat";

static const std::string HAT_PATH = DATA_PATH + "hat.dat";
static const std::string HAT_RESULT_PATH = DATA_PATH + "hat_res.dat";


struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;

    pixel()
        : r(0)
        , g(0)
        , b(0)
    {}

    pixel(uint8_t r, uint8_t g, uint8_t b)
        : r(r)
        , g(g)
        , b(b)
    {}

    uint64_t diff(const pixel& other) {
        return    abs(static_cast<int32_t>(r) - other.r)
                + abs(static_cast<int32_t>(g) - other.g)
                + abs(static_cast<int32_t>(b) - other.b);
    }
};

using image = vector<vector<pixel>>;


struct rectangle {
    uint32_t x1, y1, x2, y2;
};

image subimage(const image& source, const rectangle& frame)
{
    uint32_t h = frame.y2 - frame.y1;
    uint32_t w = frame.x2 - frame.x1;

    image res(h, std::vector<pixel>(w));

    for(uint32_t y = frame.y1; y < frame.y2; ++y) {
        for(uint32_t x = frame.x1; x < frame.x2; ++x) {
            res[y-frame.y1][x-frame.x1] = source[y][x];
        }
    }

    return res;
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

class RectangleProviderBody {
private:
    uint32_t sourceHeight, sourceWidth, findHeight, findWidth;
    uint32_t currX, currY;

public:
    RectangleProviderBody(const image& source, const image& find) {
        sourceHeight = source.size();
        sourceWidth = source[0].size();

        findHeight = find.size();
        findWidth = find[0].size();

        currX = currY = 0;
    }

    bool operator()(rectangle& res) {
        if(currX + findWidth > sourceWidth) {
            currX = 0;
            currY++;
        }

        if(currY + findHeight > sourceHeight) {
            return false;
        } else {
            res = {currX, currY, currX + findWidth, currY + findHeight};
            currX++;
            return true;
        }
    }
};

struct DiffResult {
    uint64_t diff;
    uint32_t x, y;

    DiffResult(uint64_t diff, uint32_t x, uint32_t y)
        : diff(diff)
        , x(x)
        , y(y)
    {}

    DiffResult()
        : diff(static_cast<uint64_t>(-1))
        , x(0)
        , y(0)
    {}
};

class DiffCalculatorBody {
private:
    image source, find;

public:
    DiffCalculatorBody(const image& source, const image& find)
        : source(source)
        , find(find)
    {}

    

    DiffResult operator()(const rectangle& frame) {
        DiffResult res {0, frame.x1, frame.y1};

        for(uint32_t y = frame.y1; y < frame.y2; ++y) {
            for(uint32_t x = frame.x1; x < frame.x2; ++x) {
                res.diff += source[y][x].diff(find[y-frame.y1][x-frame.x1]);
            }
        }

        return res;
    }
};

void findImages(const std::string& sourceImagePath, const std::string& findImagePath, const std::string& resultImagePath) {
    graph flowGraph;

    image sourceImage = imread(sourceImagePath);
    image findImage = imread(findImagePath);


    source_node<rectangle> rectGenNode(flowGraph, RectangleProviderBody(sourceImage, findImage), true);
    buffer_node<rectangle> genBufferNode(flowGraph);

    function_node<rectangle, DiffResult> diffCalculatorNode(flowGraph, unlimited, DiffCalculatorBody(sourceImage, findImage));
    buffer_node<DiffResult> diffBufferNode(flowGraph);

    DiffResult res;
    function_node<DiffResult> minDiffFinderNode(flowGraph, unlimited,
    [&res](const DiffResult& diff) {
        if(diff.diff < res.diff) {
            res = diff;
        }
    });

    make_edge(rectGenNode, genBufferNode);
    make_edge(genBufferNode, diffCalculatorNode);
    make_edge(diffCalculatorNode, diffBufferNode);
    make_edge(diffBufferNode, minDiffFinderNode);

    flowGraph.wait_for_all();
    
    std::cout << "closest find is at: " << res.x << ", " << res.y << std::endl;

    image findSubImage = subimage(sourceImage, {res.x, res.y, res.x + findImage[0].size(), res.y + findImage.size()});

    imwrite(findSubImage, resultImagePath);
}

int main() {
    findImages(SOURCE_PATH, CHEER_PATH, CHEER_RESULT_PATH);
    findImages(SOURCE_PATH, CHICKEN_PATH, CHICKEN_RESULT_PATH);
    findImages(SOURCE_PATH, HAT_PATH, HAT_RESULT_PATH);    
    
    return 0;
}