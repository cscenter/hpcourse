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

struct frame {
    frame()
            : x(0), y(0), w(0), h(0) {}

    frame(uint32_t x, uint32_t y, uint32_t w, uint32_t h)
            : x(x), y(y), w(w), h(h) {}

    uint32_t x;
    uint32_t y;
    uint32_t w;
    uint32_t h;
};

using image = vector<vector<pixel>>;

class FrameCreator {
private:
    int64_t n = 0, i = 0;
    frame *frames;

public:
    FrameCreator(const image *im, const image *small) {

        int64_t hh = im->size();
        int64_t ww = (*im)[0].size();

        int64_t h = small->size();
        int64_t w = (*small)[0].size();

        n = max((hh - h) * (ww - w), (int64_t) 0);
        frames = new frame[n];

        for (int64_t x = 0, i = 0; x < hh - h; ++x) {
            for (int64_t y = 0; y < ww - w; ++y) {
                frames[i++] = frame(x, y, w, h);
            }
        }
    }

    bool operator()(frame &result) {
        if (!frames) return false;
        if (i == n) {
            delete[] frames;
            frames = nullptr;
            return false;
        }
        frame next = frames[i++];
        result = next;
        return true;
    }
};

class ImgComparator {
private:
    const image *im;
    const image *small;

public:
    ImgComparator(const image *im, const image *small) {
        this->im = im;
        this->small = small;
    }

    std::pair<frame, int64_t> operator()(const frame &region) {
        int64_t res = 0;

        for (int64_t x = 0; x < region.h; ++x) {
            for (int64_t y = 0; y < region.w; ++y) {
                res += compare((*im)[region.x + x][region.y + y], (*small)[x][y]);
            }
        }
        return std::make_pair(region, res);
    }

private:
    int64_t compare(const pixel &l, const pixel &r) {
        return (int64_t) (abs(l.r - r.r) +
                          abs(l.g - r.g) +
                          abs(l.b - r.b));
    }
};

class Minimizer {
private:
    int64_t min;
    frame *res;

public:
    Minimizer(frame *res) {
        min = LONG_MAX;
        this->res = res;
    }

    frame operator()(std::pair<frame, int64_t> p) {
        frame &region = p.first;
        int64_t diff = p.second;

        if (diff < min) {
            min = diff;
            *res = frame(region);
        }

        return *res;
    }
};

image cutImage(const image &src, const frame &f) {
    auto data = vector<vector<pixel>>(f.h);
    for (auto &row: data) {
        row.resize(f.w);
    }

    for (uint64_t x = 0, xx = f.x; xx != f.x + f.h; x++, xx++) {
        for (uint64_t y = 0, yy = f.y; yy != f.y + f.w; y++, yy++) {
            data[x][y] = src[xx][yy];
        }
    }
    return data;
}

image imread(const std::string &path) {
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

    if (!file.is_open()) {
        cerr << "Can not open the file" << endl;
        throw invalid_argument(path);
    }

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

class Writer {
private:
    image im;
    std::string name;

public:
    Writer(const image &im, const string &name) : im(im), name(name) {}

    void operator()(frame res) {
        imwrite(cutImage(im, res), name + "_res.dat");
        std::cout << name << " is done " << "\n";
    }
};


void find(const image *im, const std::string &path, const std::string &name) {
    image small = imread(path);
    frame *res = new frame(0, 0, 0, 0);
    tbb::flow::graph g;

    source_node<frame> sourceNode(g, FrameCreator(im, &small), false);
    buffer_node<frame> frameBuffer(g);
    function_node<frame, std::pair<frame, int64_t>> comparingNode(g, unlimited, ImgComparator(im, &small));
    function_node<std::pair<frame, int64_t>, frame> minimizingNode(g, serial, Minimizer(res));

//    overwrite_node<frame> resBuffer(g);
//    function_node<frame> writeNode(g, 1, Writer(*im, name));

    make_edge(sourceNode, frameBuffer);
    make_edge(frameBuffer, comparingNode);
    make_edge(comparingNode, minimizingNode);
//    make_edge(minimizingNode, resBuffer);
//    make_edge(resBuffer, writeNode);

    sourceNode.activate();
    g.wait_for_all();

    image cutI = cutImage(*im, *res);
    imwrite(cutI, name + "_res.dat");
    std::cout << name << " is done " << "\n";
    delete res;
}

int main() {
    image const im = imread("../data/image.dat");

    find(&im, "../data/hat.dat", "hat");
    find(&im, "../data/chicken.dat", "chicken");
    find(&im, "../data/cheer.dat", "cheer");
    return 0;
}