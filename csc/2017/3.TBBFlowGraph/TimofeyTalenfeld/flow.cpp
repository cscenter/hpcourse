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

struct rect {
    size_t top;
    size_t right;
    size_t bottom;
    size_t left;

    rect(): top(0), right(0), bottom(0), left(0) {}

    friend ostream& operator << (ostream& s, const rect &r) {
        s << "top: " << r.top
          << " right: " << r.right
          << " bottom: " << r.bottom
          << " left: " << r.left
          << '\n';
        return s;
    }
};

using image = vector<vector<pixel>>;

uint32_t diff(pixel f, pixel s) {
    return abs(f.r - s.r) + abs(f.g - s.g) + abs(f.b - s.b);
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

class rect_generator {
public:
    rect_generator(const image &picture, const image inner_picture) {
        size_t height = picture.size();
        size_t width = picture.front().size();
        size_t inner_height = inner_picture.size();
        size_t inner_width = inner_picture.front().size();

        for (size_t y = 0; y < height - inner_height; ++y) {
            for (size_t x = 0; x < width - inner_width; ++x) {
                rect r;
                r.top = y;
                r.right = x + inner_width - 1;
                r.bottom = y + inner_height - 1;
                r.left = x;
                rectangles.push_back(r);
            }
        }
    };
    bool operator() (rect &r) {
        if (rectangles.empty()) {
            return false;
        }
        r = rectangles.back();
        rectangles.pop_back();
        return true;
    }
private:
    vector<rect> rectangles;
};

void write_rect_picture(const image &picture, const rect &rect, const string &data_path) {
    image pic(rect.bottom - rect.left + 1, vector<pixel>(rect.right - rect.left + 1));

    for (size_t y = 0; y != rect.bottom - rect.top + 1; ++y) {
        for (size_t x = 0; x != rect.right - rect.left + 1; ++x) {
            pic[y][x] = picture[y + rect.top][x + rect.left];
        }
    }
    imwrite(pic, data_path);
}


void find_rect(const image &picture, const image &inner_picture, string data_path) {

    graph g;

    source_node<rect> rect_generator_node(g, rect_generator(picture, inner_picture), true);

    buffer_node<rect> rect_generator_buffer_node(g);

    function_node<rect, pair<rect, uint32_t>> diff_evaluator_node(
        g, unlimited,
        [&] (const rect &r) -> pair<rect, uint32_t> {

            uint32_t d = 0;

            size_t height = inner_picture.size();
            size_t width = inner_picture.front().size();

            for (size_t y = 0; y < height; ++y) {
                for (size_t x = 0; x < width; ++x) {
                    d += diff(picture[y + r.top][x + r.left], inner_picture[y][x]);
                }
            }
            return {r, d};
        }
    );

    buffer_node<pair<rect, uint32_t>> diff_evaluator_buffer_node(g);

    uint32_t min_diff = std::numeric_limits<uint32_t>::max();
    rect min_diff_rect;

    function_node<pair<rect, uint32_t>> min_diff_finder_node(
        g, 1,
        [&] (const pair<rect, uint32_t> p) {
            rect r = p.first;
            uint32_t diff = p.second;
            if (diff < min_diff) {
                min_diff = diff;
                min_diff_rect = r;
            }
        }
    );

    make_edge(rect_generator_node, rect_generator_buffer_node);
    make_edge(rect_generator_buffer_node, diff_evaluator_node);
    make_edge(diff_evaluator_node, diff_evaluator_buffer_node);
    make_edge(diff_evaluator_buffer_node, min_diff_finder_node);
    g.wait_for_all();

    cout << min_diff_rect;
    write_rect_picture(picture, min_diff_rect, data_path);
}


int main() {
    image picture = imread("../data/image.dat");
    find_rect(picture, imread("../data/hat.dat"), "../data/hat_1.dat");
    find_rect(picture, imread("../data/chicken.dat"), "../data/chicken_1.dat");
    find_rect(picture, imread("../data/cheer.dat"), "../data/cheer_1.dat");
    return 0;
}
