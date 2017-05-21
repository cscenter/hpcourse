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
//image get_test_big_img() {
//    auto data = vector<vector<pixel>>(3);
//    for (auto &row: data) {
//        row.resize(3);
//    }
//    data[0][0] = pixel {10, 9, 10};
//    data[0][1] = pixel {10, 9, 10};
//    data[0][2] = pixel {10, 9, 10};
//    data[1][0] = pixel {8, 10, 8};
//    data[1][1] = pixel {8, 10, 8};
//    data[1][2] = pixel {8, 10, 8};
//    data[2][0] = pixel {10, 10, 10};
//    data[2][1] = pixel {10, 10, 10};
//    data[2][2] = pixel {10, 10, 10};
//    return data;
//}
//image get_test_img() {
//    auto data = vector<vector<pixel>>(2);
//    for (auto &row: data) {
//        row.resize(2);
//    }
//    data[0][0] = pixel {8, 10, 8};
//    data[0][1] = pixel {8, 10, 8};
//    data[1][0] = pixel {11, 10, 10};
//    data[1][1] = pixel {10, 10, 10};
//    return data;
//}

image big_img = imread("data/image.dat");
//image big_img = get_test_big_img();

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

    size_t get_height() const {
        return (size_t) abs(y_bottom - y_top) + 1;
    }

    size_t get_width() const {
        return (size_t) abs(x_right - x_left) + 1;
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

image get_sub_img(const ImgSlice &slice) {
    size_t height = slice.get_height();
    size_t width = slice.get_width();

    auto data = vector<vector<pixel>>(height);
    for (auto &row: data) {
        row.resize(width);
    }

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            data[y][x] = big_img[slice.y_top + y][slice.x_left + x];
        }
    }

    return data;
}

void find_sub_img(const string &img_path) {
    image img = imread(img_path);
//    image img = get_test_img();
    graph g;

    const ImgSlicer &imgSlicer = ImgSlicer(img);
    source_node<ImgSlice> slicer(g, imgSlicer, false);

    buffer_node<ImgSlice> slices_buffer(g);

    const DiffEvaluator &evaluator = DiffEvaluator(img);
    function_node<ImgSlice, tuple<ImgSlice, uint64_t>> diff_evaluator(g, unlimited, evaluator);

    queue_node<tuple<ImgSlice, uint64_t>> diff_queue(g);

    ImgSlice min_diff_slice;
    uint64_t min_diff = UINT64_MAX;
    function_node<tuple<ImgSlice, uint64_t>> min_diff_slice_selector(
            g, serial,
            [&min_diff_slice, &min_diff](const tuple<ImgSlice, uint64_t> &t) {
                ImgSlice slice = get<0>(t);
                uint64_t diff = get<1>(t);
                if (diff < min_diff) {
                    min_diff = diff;
                    min_diff_slice = slice;
                }
            }
    );

    make_edge(slicer, slices_buffer);
    make_edge(slices_buffer, diff_evaluator);
    make_edge(diff_evaluator, diff_queue);
    make_edge(diff_queue, min_diff_slice_selector);
    slicer.activate();
    g.wait_for_all();

    cout << img_path << endl;
    cout << "min diff: " << min_diff << endl;
    cout << "coords: (" << min_diff_slice.x_left << ", " << min_diff_slice.y_top << ")";
    cout << "(" << min_diff_slice.x_right << ", " << min_diff_slice.y_bottom << ")" << endl;
    image sub_img = get_sub_img(min_diff_slice);
    imwrite(sub_img, img_path + "_result");
}
//-----------------------------

int main(int argc, char *argv[]) {
    find_sub_img("data/cheer.dat");
    find_sub_img("data/chicken.dat");
    find_sub_img("data/hat.dat");
    return 0;
}
