#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <unistd.h>
#include <random>
#include <memory>
#include <cassert>

#include <tbb/flow_graph.h>
#include <tbb/spin_mutex.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

long absdiff(pixel &a, pixel &b) {
    return abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b);
}

template<class T> struct GenericWindow {
    T x, y, w, h;
    void operator=(GenericWindow<T> const &other) {
        x = other.x; y = other.y; w = other.w; h = other.h;
    }
};


using Window = GenericWindow<long>;

ostream & operator<<(ostream &stream, Window &window) {
    stream << '(' << window.x << ", " << window.y << ", " << window.w << ", " << window.h << ')';
    return stream;
}

using image = vector<vector<pixel>>;

image imread(const std::string& path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        cerr << "Can read only prepared .dat files!" << endl;
        throw invalid_argument(path);
    }

    ifstream file(path, ios::binary | ios::in);
    assert(file.good());

    std::uint32_t h, w, d;
    file.read(reinterpret_cast<char*>(&h), sizeof(h));
    file.read(reinterpret_cast<char*>(&w), sizeof(w));
    file.read(reinterpret_cast<char*>(&d), sizeof(d));

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

image getSubImage(image input, Window window) {
    image out(window.h, std::vector<pixel>(window.w, {0, 0, 0}));
    for (int row = 0; row < window.h; row++) {
        for (int col = 0; col < window.w; col++) {
            out[row][col] = input[window.y + row][window.x + col];
        }
    }
    return out;
}

void findClosestSubimage(string const &inputImagePath, string const &patternPath, string const &outputPath) {
    image pattern = imread(patternPath);
    image inputImage = imread(inputImagePath);

    graph g;

    long row = 0, col = 0;

    source_node<Window> windowsGenerator(g, [&pattern, &inputImage, &row, &col] (Window &val)
    {
        if (col > inputImage[0].size() - pattern[0].size()) {
            col = 0;
            row++;
        }

        val = {col, row, static_cast<decltype(col)>(pattern[0].size()), static_cast<decltype(row)>(pattern.size())};
        col++;
        return row <= static_cast<long>(inputImage.size() - pattern.size());
    }, false);

    buffer_node<Window> windowBuffer(g);

    tbb::spin_mutex io_mutex;

    function_node<Window, pair<Window, int>> scoreCalculator(g, unlimited, [&pattern, &inputImage, &io_mutex] (Window w) {
        {
            tbb::spin_mutex::scoped_lock lock(io_mutex);
            cout << "Processing window with params " << w << '\n';
        }
        long sum = 0;
        for (int row = 0; row < w.h; row++) {
            for (int col = 0; col < w.w; col++) {
                sum += absdiff(pattern[row][col], inputImage[row + w.y][col + w.x]);
            }
        }
        return make_pair(w, sum);
    });

    buffer_node<pair<Window, int>> scoreBuffer(g);

    Window minWindow;
    long minDiff = 255 * 3 * pattern.size() * pattern[0].size() + 1;

    function_node<pair<Window, int>, int> minReducer(g, serial, [&] (pair<Window, int> p) {
        if (p.second < minDiff) {
            minWindow = p.first;
            minDiff = p.second;
        }

        return -1;
    });

    function_node<Window> printSize(g, 1, [] (Window w) { cout << w; });

    make_edge(windowsGenerator, windowBuffer);
    make_edge(windowBuffer, scoreCalculator);
    make_edge(scoreCalculator, scoreBuffer);
    make_edge(scoreBuffer, minReducer);

    windowsGenerator.activate();
    g.wait_for_all();


    cout << "Minimal window: " << minWindow << "; difference was " << minDiff << '\n';
    imwrite(getSubImage(inputImage, minWindow), outputPath);
}

int main(int argc, char *argv[]) {
    cout << "Processing chicken.dat:\n";
    findClosestSubimage("../data/image.dat", "../data/chicken.dat", "../data/found_chicken.dat");

    cout << "Processing hat.dat:\n";
    findClosestSubimage("../data/image.dat", "../data/hat.dat", "../data/found_hat.dat");

    cout << "Processing cheer.dat:\n";
    findClosestSubimage("../data/image.dat", "../data/cheer.dat", "../data/found_cheer.dat");

    return 0;
}