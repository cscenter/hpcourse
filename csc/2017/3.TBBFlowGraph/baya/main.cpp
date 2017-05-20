#include <iostream>
#include <fstream>
#include <algorithm>
#include <vector>
#include <queue>
#include <array>

#include <tbb/flow_graph.h>
#include <tbb/parallel_for.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

using image = vector<vector<pixel>>;


image big_image;
image small_image;

image imread(const std::string &path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        cerr << "Can read only prepared .dat files!" << endl;
        throw invalid_argument(path);
    }

    ifstream file(path, ios::binary | ios::in);

//    if (!file.is_open()) {
//        cerr << "Can not open the file" << endl;
//        throw invalid_argument(path);
//    }

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


struct piece_in_big {
    size_t top_x, top_y, bottom_x, bottom_y;

    piece_in_big() : top_x(0), top_y(0), bottom_x(0), bottom_y(0) {}

    piece_in_big(size_t x, size_t y, size_t xx, size_t yy) : top_x(x), top_y(y), bottom_x(xx), bottom_y(yy) {}

};

struct cut {
private:
    queue<piece_in_big> pieces;
public:
    cut() {
        size_t big_h = big_image.size();
        size_t big_w = big_image.front().size();
        size_t small_h = small_image.size();
        size_t small_w = small_image.front().size();

        for (size_t y = 0; y < big_h - small_h; ++y) {
            for (size_t x = 0; x < big_w - small_w; ++x) {
                pieces.push(piece_in_big(x, y, x + small_w - 1, y + small_h - 1));
            }
        }
    }

    bool operator()(piece_in_big &result) {
        if (pieces.empty()) {
            return false;
        }
        result = pieces.front();
        pieces.pop();
        return true;
    }

};

long get_diff(pixel &a, pixel &b) {
    return abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b);
}

struct difference {

    tuple<piece_in_big, int64_t> operator()(const piece_in_big &piece) {
        int64_t difference = 0;
        size_t s_h = small_image.size();
        size_t s_w = small_image.front().size();
        for (size_t y = 0; y < s_h; ++y) {
            for (size_t x = 0; x < s_w; ++x) {
                difference += get_diff(big_image[piece.top_y + y][piece.top_x + x],
                                       small_image[y][x]);
            }
        }

        return make_tuple(piece, difference);
    }
};

void solve(const string &s_p, const string &r_p) {
    big_image = imread("data/image.dat");
    small_image = imread(s_p);
    graph g;

    piece_in_big min_piece;
    int64_t min = numeric_limits<int64_t>::max();
    typedef tuple<piece_in_big, int64_t> difference_pair;

    source_node<piece_in_big> cutting(g, cut(), false);
    buffer_node<piece_in_big> piecies(g);

    function_node<piece_in_big, difference_pair> get_difference(g, unlimited, difference());
    buffer_node<difference_pair> res_buf(g);

    function_node<difference_pair> find_min_diff(g, 1, [&](difference_pair cur) -> void {
        if (get<1>(cur) < min) {
            min = get<1>(cur);
            min_piece = get<0>(cur);
        }
    });

    make_edge(cutting, piecies);
    make_edge(piecies, get_difference);
    make_edge(get_difference, res_buf);
    make_edge(res_buf, find_min_diff);

    cutting.activate();
    g.wait_for_all();

    cout << "Top left corner coords:\n";
    cout << "(" << min_piece.top_x << ", " << min_piece.top_y << ")" << endl;


    image result;
    for (int64_t y = 0; y < min_piece.bottom_y - min_piece.top_y; ++y) {
        vector<pixel> line;
        for (int64_t x = 0; x < min_piece.bottom_x - min_piece.top_x; ++x) {
            line.push_back(big_image[min_piece.top_y + y][min_piece.top_x + x]);
        }
        result.push_back(line);
    }


    imwrite(result, s_p.substr(0, s_p.size() - 4) + "_res.dat");

}


int main() {
    solve("data/cheer.dat", "data/res_cheer.dat");
    solve("data/hat.dat", "data/res_hat.dat");
    solve("data/chicken.dat", "data/res_chicken.dat");
    return 0;
}
