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

struct ROI {
    int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
    ROI() {}
    ROI(int x1, int y1, int x2, int y2) : x1(x1), y1(y1), x2(x2), y2(y2) {}
};

struct GenerateROIs {
    list<ROI> ROIs;

    GenerateROIs(const image &small_image, const image &big_image) {
        int x_max_small = small_image.size();
        int y_max_small = small_image[0].size();
        int x_max_full = big_image.size();
        int y_max_full = big_image[0].size();

        for (int i = 0; i < x_max_full - x_max_small; ++i) {
            for (int j = 0; j < y_max_full - y_max_small; ++j) {
                ROIs.push_back(ROI (i, j, i + x_max_small, j + y_max_small));
            }
        }
    }

    bool operator()(ROI &result) {
        if (ROIs.empty())
            return false;

        result = ROIs.front();
        ROIs.pop_front();

        return true;
    }
};

struct CalcDiff {
    image big_image;
    image small_image;

    CalcDiff(const image &small_image, const image &big_image) : big_image(big_image), small_image(small_image) {}

    pair<ROI, int> operator() (const ROI& roi) {
        int diff = 0;
        int h = small_image.size();
        int w = small_image[0].size();
        for (int i = 0; i < h; ++i) {
            for (int j = 0; j < w; ++j) {
                diff += pixels_diff(big_image[roi.x1 + i][roi.y1 + j], small_image[i][j]);
            }
        }

        return pair<ROI,int>(roi, diff);
    }

    static int pixels_diff(const pixel &pxl1, const pixel &pxl2) {
        return abs((int)pxl1.r - (int)pxl2.r) + abs((int)pxl1.b - (int)pxl2.b) + abs((int)pxl1.g - (int)pxl2.g);
    }
};

struct FindMax {
    int min_diff = INT_MAX;
    ROI& result;

    FindMax (ROI& result) : result(result) {}

    ROI operator()(pair<ROI,int> roi_and_diff) {
        ROI &roi = roi_and_diff.first;
        int diff = roi_and_diff.second;

        if (diff < min_diff) {
            min_diff = diff;
            result = roi;
        }

        return result;
    }
};

image copyROI(const image &img, const ROI &roi) {
    int h = roi.x2 - roi.x1;
    int w = roi.y2 - roi.y1;

    auto data = vector<vector<pixel>>(h);
    for (auto &row : data)
        row.resize(w);

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            data[i][j] = img[i + roi.x1][j + roi.y1];
        }
    }

    return data;
}

void execute(const string &small_img_path) {
    graph g;

    string full_img_path = "data/image.dat";

    image full_image = imread(full_img_path);
    image small_image = imread(small_img_path);

    ROI result;

    source_node<ROI> gen_rois(g, GenerateROIs(small_image, full_image), false);
    buffer_node<ROI> gen_rois_buffer(g);
    function_node<ROI, pair<ROI,int>> calc_diff(g, unlimited, CalcDiff(small_image, full_image));
    buffer_node<pair<ROI, int>> calc_diff_buffer(g);
    function_node<pair<ROI, int>, ROI> find_max(g, 1, FindMax(result));

    make_edge(gen_rois, gen_rois_buffer);
    make_edge(gen_rois_buffer, calc_diff);
    make_edge(calc_diff, calc_diff_buffer);
    make_edge(calc_diff_buffer, find_max);

    gen_rois.activate();
    g.wait_for_all();

    cout << small_img_path << ": (" << result.x1 << ", " << result.y1 << ")" << endl;
    imwrite(copyROI(full_image, result), small_img_path + "_");
}

int main() {
    execute("data/cheer.dat");
    execute("data/hat.dat");
    execute("data/chicken.dat");

    return 0;
}
