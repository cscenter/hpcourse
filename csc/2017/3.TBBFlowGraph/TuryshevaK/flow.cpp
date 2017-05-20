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

struct point {
    size_t x, y;
};

using image = vector<vector<pixel>>;

void find_picture(const image &main_image, const image &image, const string string);

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

struct picture_coordinates {
    point top_left, bottom_right;
};

class Slicer {
    queue<picture_coordinates> slices;
public:

    Slicer(image main_picture, image search_picture) {
        size_t picture_size_x = main_picture.front().size();
        size_t picture_size_y = main_picture.size();

        size_t search_picture_size_x = search_picture.front().size();
        size_t search_picture_size_y = search_picture.size();

        for (size_t top_left_x = 0; top_left_x != picture_size_x - search_picture_size_x; top_left_x++) {
            for (size_t top_left_y = 0; top_left_y != picture_size_y - search_picture_size_y; top_left_y++) {
                picture_coordinates slice_coordinates;
                point top_left, bottom_right;

                top_left.x = top_left_x;
                top_left.y = top_left_y;

                bottom_right.x = top_left_x + search_picture_size_x - 1;
                bottom_right.y = top_left_y + search_picture_size_y - 1;

                slice_coordinates.top_left = top_left;
                slice_coordinates.bottom_right = bottom_right;

                slices.push(slice_coordinates);
            }
        }
    };

    bool operator()(picture_coordinates &picture) {
        if (slices.empty())
            return false;

        picture = slices.front();
        slices.pop();

        return true;
    }
};

int32_t diff_score(pixel a, pixel b) {
    int r_diff = abs(a.r - b.r);
    int g_diff = abs(a.g - b.g);
    int b_diff = abs(a.b - b.b);
    return r_diff + g_diff + b_diff;
}


class DiffScoreMapper {
    image main_picture;
    image search_picture;

public:

    DiffScoreMapper(image _main_picture, image _search_picture): main_picture(_main_picture), search_picture(_search_picture) {};

    pair<picture_coordinates, int64_t> operator()(const picture_coordinates &picture_coordinates) {
        int64_t diff = 0;

        for (size_t x = 0; x != search_picture.front().size(); x++) {
            for (size_t y = 0; y != search_picture.size(); y++) {
                diff += diff_score(
                        main_picture[picture_coordinates.top_left.y + y][picture_coordinates.top_left.x + x],
                        search_picture[y][x]
                );
            }
        }

        return make_pair(picture_coordinates, diff);
    }
};

void write_image(picture_coordinates picture_coordinates, string out_path, image picture) {
    size_t x_size = picture_coordinates.bottom_right.x - picture_coordinates.top_left.x + 1;
    size_t y_size = picture_coordinates.bottom_right.y - picture_coordinates.top_left.y + 1;
    image new_image(
            y_size,
            vector<pixel>(x_size)
    );

    for (size_t x = 0; x != x_size; ++x) {
        for (size_t y = 0; y != y_size; ++y) {
            new_image[y][x] = picture[y + picture_coordinates.top_left.y][x + picture_coordinates.top_left.x];
        }
    }
    imwrite(new_image, out_path);
}


int main() {
    const image &main_image = imread("../data/image.dat");
    const image &hat_image = imread("../data/hat.dat");
    const image &chicken_image = imread("../data/chicken.dat");
    const image &cheer_image = imread("../data/cheer.dat");

    find_picture(main_image, hat_image, "../data/hat_result.dat");
    find_picture(main_image, chicken_image, "../data/chicken_result.dat");
    find_picture(main_image, cheer_image, "../data/cheer_result.dat");

    return 0;
}


void find_picture(const image &main_image, const image &image, const string out_path) {
    cout << "processing result to " << out_path << "\n";

    graph calculation_graph;

    source_node<picture_coordinates> slicerNode(calculation_graph, Slicer(main_image, image), true);
    buffer_node<picture_coordinates> slicerBufferNode(calculation_graph);


    function_node<picture_coordinates, pair<picture_coordinates, int64_t>> diffScoreNode(
            calculation_graph,
            unlimited,
            DiffScoreMapper(main_image, image)
    );


    buffer_node<pair<picture_coordinates, int64_t>> diffScoreBufferNode(calculation_graph);

    int64_t min = numeric_limits<int64_t>::max();
    picture_coordinates best_coordinates;

    auto find_best_coordinates = [&](pair<picture_coordinates, int64_t> args) {
                picture_coordinates &cur_coordinates = args.first;
                int64_t cur_diff_score = args.second;
                if (cur_diff_score < min) {
                    min = cur_diff_score;
                    best_coordinates = cur_coordinates;
                }
            };

    function_node<pair<picture_coordinates, int64_t>> findMinNode(calculation_graph, 1, find_best_coordinates);

    make_edge(slicerNode, slicerBufferNode);
    make_edge(slicerBufferNode, diffScoreNode);
    make_edge(diffScoreNode, diffScoreBufferNode);
    make_edge(diffScoreBufferNode, findMinNode);

    calculation_graph.wait_for_all();


    cout << "top_left: (" << best_coordinates.top_left.x << ", " << best_coordinates.top_left.y << ')'
         << "\n"
         << "bottom_right: (" << best_coordinates.bottom_right.x << ", " << best_coordinates.bottom_right.y << ")"
         << "\n";

    write_image(best_coordinates, out_path, main_image);
}


