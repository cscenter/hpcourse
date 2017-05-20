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

struct slice {
    int x;
    int y;
    int h;
    int w;
};

class Slicer {
    queue<slice> slices;
public:
    Slicer(image img, int h, int w) {
        for (int i = 0; i < img.size() - h; i++) {
            for (int j = 0; j < img[0].size() - w; j++) {
                slices.push(slice{i, j, h, w});
            }
        }
    }

    bool operator() (slice &s) {
        if (slices.empty()) {
            return false;
        }
        s = slices.front();
        slices.pop();
        return true;
    }    
};

long diffPixels(const pixel &p1, const pixel &p2) {
    return abs(p1.r - p2.r) + abs(p1.g - p2.g) + abs(p1.b - p2.b);
}

class Comparator {
    image img1;
    image img2;
public:
        Comparator(image img1, image img2): img1(img1), img2(img2) {
        }
        
        pair<slice, long> operator() (const slice &s) {
        long diff = 0;
        for (int i = 0; i < s.h; i++) {
            for (int j = 0; j < s.w; j++) {
                diff += diffPixels(img1[s.x + i][s.y + j], img2[i][j]);
            }
        }
        return {s, diff};
    }
};

class Min {
    long min = LONG_MAX;
    slice &s;

public:
    Min(slice &s): s(s) {
    }

    void operator() (const pair<slice, long> &p) {
        if (p.second < min) {
            min = p.second;
            s = p.first;
        }
    }
};

image getSub(image img, slice s) {
    auto data = vector<vector<pixel>>(s.h);
    for (auto& row: data) {
        row.resize(s.w);
    }
    for (int i = 0; i < s.h; ++i) {
        for (int j = 0; j < s.w; ++j) {
            data[i][j] = img[s.x + i][s.y + j];
        }
    }
    return data;
}

void findSub(const string &fullImagePath, const string &subImagePath, const string &outImagePath) {
    image subImage = imread(subImagePath);
    image fullImage = imread(fullImagePath);
    int subImageHeight = subImage.size();
    int subImageWidth = subImage[0].size();
    
    slice result;
    graph g;
    source_node<slice> sliceNode(g, Slicer(fullImage, subImageHeight, subImageWidth));
    buffer_node<slice> slicesBufferNode(g);
    function_node<slice, pair<slice, long>> diffNode(g, unlimited, Comparator(fullImage, subImage));
    buffer_node<pair<slice, long>> diffBufferNode(g);
    function_node<pair<slice, long>> minNode(g, unlimited, Min(result));
    
    make_edge(sliceNode, slicesBufferNode);
    make_edge(slicesBufferNode, diffNode);
    make_edge(diffNode, diffBufferNode);
    make_edge(diffBufferNode, minNode);
    g.wait_for_all();
    
    cout << result.x << "," << result.y << "," << result.w << "," << result.h << endl;
    image outImage = getSub(fullImage, result);
    imwrite(outImage, outImagePath);
}

int main() {
    findSub("data/image.dat", "data/cheer.dat", "./out-cheer.dat");
    findSub("data/image.dat", "data/chicken.dat", "./out-chicken.dat");
    findSub("data/image.dat", "data/hat.dat", "./out-hat.dat");
    return 0;
}
