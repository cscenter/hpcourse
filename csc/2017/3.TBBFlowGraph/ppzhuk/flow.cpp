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

//------------------------------
image get_test_img(unsigned long h, unsigned long w, uint8_t v) {
    auto data = vector<vector<pixel>>(h);
    for (auto &row: data) {
        row.resize(w);
    }
    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            data[i][j] = pixel {v, v, v};
        }
    }
    return data;
}

image big_img = imread("data/image.dat");
//image big_img = get_test_img(3, 3, 10);

struct ImgSlice {
    size_t x_left = 0;
    size_t x_right = 0;
    size_t y_top = 0;
    size_t y_bottom = 0;

    ImgSlice() {}

    ImgSlice(size_t x_l, size_t x_r, size_t y_t, size_t y_b) {
        x_left = x_l;
        x_right = x_r;
        y_top = y_t;
        y_bottom = y_b;
    }
};

class ImgSlicer {
    queue<ImgSlice> slices;
public:
    ImgSlicer(const image &img) {
        size_t big_img_height = big_img.size();
        size_t big_img_width = big_img[0].size();
        size_t img_height = img.size();
        size_t img_width = img[0].size();

        for (size_t y = 0; y <= big_img_height - img_height; ++y) {
            for (size_t x = 0; x <= big_img_width - img_width; ++x) {
                slices.push(
                        ImgSlice(x, x + img_width - 1, y, y + img_height - 1)
                );
            }
        }

//        cout << "slices: " << slices.size() << endl;
    }

    bool operator()(ImgSlice &res) {
        if (slices.empty()) {
            return false;
        }

        res = slices.front();
        slices.pop();
        return true;
    }
};

class DiffEvaluator {
    image img;

    size_t get_diff(const pixel &p1, const pixel &p2) {
        return (size_t) (abs(p1.r - p2.r) + abs(p1.g - p2.g) + abs(p1.b - p2.b));
    }

public:
    DiffEvaluator(const image &_img) {
        img = _img;
    }

    tuple<ImgSlice, uint64_t> operator()(const ImgSlice &slice) {
        uint64_t diff = 0;
        size_t img_height = img.size();
        size_t img_width = img[0].size();

        for (int y = 0; y < img_height; ++y) {
            for (int x = 0; x < img_width; ++x) {
                size_t pixel_diff = get_diff(img[y][x], big_img[slice.y_top + y][slice.x_left + x]);
//                cout << "pixel diff: " << pixel_diff << endl;
                diff += pixel_diff;
            }
        }

        return make_tuple(slice, diff);
    };
};

void find_sub_img(const string &img_path) {
    image img = imread(img_path);
//    image img = get_test_img(2, 3, 9);
    graph g;
    const ImgSlicer &imgSlicer = ImgSlicer(img);
    source_node<ImgSlice> slicer(g, imgSlicer, false);
    buffer_node<ImgSlice> slices_buffer(g);
    const DiffEvaluator &evaluator = DiffEvaluator(img);
    function_node<ImgSlice, tuple<ImgSlice, uint64_t>> diff_evaluator(g, unlimited, evaluator);

    uint64_t c = 0;
    function_node<tuple<ImgSlice, uint64_t>> end(g, unlimited, [&c](const tuple<ImgSlice, uint64_t> & t) {
        c+=get<1>(t);
    });

    make_edge(slicer, slices_buffer);
    make_edge(slices_buffer, diff_evaluator);
    make_edge(diff_evaluator, end);
    slicer.activate();
    g.wait_for_all();
    cout << c << endl;
}

//-----------------------------

int main(int argc, char *argv[]) {
    find_sub_img("data/cheer.dat");
//    find_sub_img("data/chicken.dat");
//    find_sub_img("data/hat.dat");
    return 0;
}



