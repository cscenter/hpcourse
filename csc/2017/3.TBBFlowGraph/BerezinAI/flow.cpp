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

class Rect {
    size_t left_x = 0,
            left_y = 0,
            right_x = 0,
            right_y = 0;

public:
    Rect() {};
    Rect(const size_t left_x_param,
         const size_t left_y_param,
         const size_t right_x_param,
         const size_t right_y_param): left_x(left_x_param),
                                      left_y(left_y_param),
                                      right_x(right_x_param),
                                      right_y(right_y_param) {};

    size_t getLeftX() const {
        return left_x;
    }

    size_t getLeftY() const {
        return left_y;
    }

    size_t getRightX() const {
        return right_x;
    }

    size_t getRightY() const {
        return right_y;
    }
    size_t width() const {
        return right_x - left_x + 1;
    }
    size_t height() const {
        return right_y - left_y + 1;
    }

    static void save(const image full, const Rect rect, const string path) {
        size_t height = rect.height(),
                width = rect.width();
        image part(height, vector<pixel>(width));
        for (size_t new_x = 0; new_x < width; ++new_x) {
            for (size_t new_y = 0; new_y < height; ++new_y) {
                part[new_y][new_x] = full[new_y + rect.getLeftY()][new_x + rect.getLeftX()];
            }
        }
        imwrite(part, path);
    }
};

class Closest {
    int64_t distance;
    Rect rect;
public:
    Closest(const int64_t the_distance,
            const Rect &the_rect): distance(the_distance),
                                   rect(the_rect) {}
    Closest(): distance(0) {}
    Closest(const int64_t the_distance): distance(0) {}

    int64_t getDistance() const {
        return distance;
    }
    void setDistance(const int64_t the_distance) {
        distance = the_distance;
    }
    Rect getRect() const {
        return rect;
    }
    void setRect(const Rect &the_rect) {
        rect = the_rect;
    }
};

class ImageFragments {
    queue<Rect> fragments;
public:
    ImageFragments(const image full, const image part) {
        const size_t part_x_size = part.front().size(),
                part_y_size = part.size();
        for (size_t x = 0; x < full.front().size() - part_x_size; ++x) {
            for (size_t y = 0; y < full.size() - part_y_size; ++y) {
                fragments.push(Rect(x, y, x + part_x_size - 1, y + part_y_size - 1));
            }
        }
    }

    bool pop(Rect &rect) {
        if (fragments.empty()) {
            return false;
        }
        rect = fragments.front(); fragments.pop();
        return true;
    }

    bool operator()(Rect &rect) {
        return pop(rect);
    }
};

class Diff {
    image full, part;
public:
    Diff(const image full_param,
         const image part_param): full(full_param),
                                  part(part_param) {}

    static int32_t count(const pixel p1, const pixel p2) {
        return abs(p1.r - p2.r) +
                abs(p1.g - p2.g) +
                abs(p1.b - p2.b);
    }

    Closest count(const Rect &rect) {
        Closest c = Closest(0, rect);
        for (size_t x = 0; x < part.front().size(); ++x) {
            for (size_t y = 0; y < part.size(); ++y) {
                c.setDistance(c.getDistance() + count(
                        full[rect.getLeftY() + y][rect.getLeftX() + x],
                        part[y][x]
                ));
            }
        }
        return c;
    }

    Closest operator()(const Rect &rect) {
        return count(rect);
    }
};

Closest find(const string partPath, const image full) {
    image part = imread(partPath);

    graph g;
    source_node<Rect> to_create(g, ImageFragments(full, part), true);
    buffer_node<Rect> buffered1(g);
    function_node<Rect, Closest> to_diff(g, unlimited, Diff(full, part));
    buffer_node<Closest> buffered2(g);

    Closest optimal = Closest((int64_t)numeric_limits<int64_t>::max());
    function_node<Closest> to_find(g, 1, [&](Closest c) {
        if (c.getDistance() < optimal.getDistance()) {
            optimal = c;
        }
    });

    make_edge(to_create, buffered1);
    make_edge(buffered1, to_diff);
    make_edge(to_diff, buffered2);
    make_edge(buffered2, to_find);

    g.wait_for_all();

    return optimal;
}

int main() {
    image full = imread("../data/image.dat");
    Rect::save(full, find("../data/hat.dat", full).getRect(), "../data/hat_beraliv.dat");
    Rect::save(full, find("../data/chicken.dat", full).getRect(), "../data/chicken_beraliv.dat");
    Rect::save(full, find("../data/cheer.dat", full).getRect(), "../data/cheer_beraliv.dat");
    return 0;
}