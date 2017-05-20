#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

string dirWithData = "../data/";

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

using point = tuple<int, int>;
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

    uint32_t h, w, d;
    file.read(reinterpret_cast<char*>(&h), 4);
    file.read(reinterpret_cast<char*>(&w), 4);
    file.read(reinterpret_cast<char*>(&d), 4);

    auto data = vector<vector<pixel>>(h);
    for (auto& row : data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = pixel{ uint8_t(pix[0]),
                uint8_t(pix[1]),
                uint8_t(pix[2]) };
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
        for (auto& pix : row) {
            file.write(reinterpret_cast<const char*>(&pix.r), 1);
            file.write(reinterpret_cast<const char*>(&pix.g), 1);
            file.write(reinterpret_cast<const char*>(&pix.b), 1);
        }
    }
    file.close();
}

class generator {
private:
    queue<point> points;

public:
    generator(point bigCoord, point smallCoord) {
        int bigHeight = get<0>(bigCoord);
        int bigWidth = get<1>(bigCoord);
        int smallHeight = get<0>(smallCoord);
        int smallWidth = get<1>(smallCoord);
        for (int i = 0; i < bigHeight - smallHeight; i++) {
            for (int j = 0; j < bigWidth - smallWidth; j++) {
                points.push(make_tuple(i, j));
            }
        }
    }

    bool operator()(point &result) {
        if (!points.empty()) {
            result = points.front();
            points.pop();
            return true;
        }
        return false;
    }
};

class minReducer {
private:
    tuple<int, point> &min;

public:
    minReducer(tuple<int, point> &t) : min(t) {}

    tuple<int, point> operator()(tuple<int, point> diffPair) {
        if (get<0>(min) > get<0>(diffPair)) {
            min = diffPair;
        }
        return min;
    }

    tuple<int, point> getMin() {
        return min;
    };
};

image subImage(image &big, point position, point smallCoord) {
    int height = get<0>(smallCoord);
    int width = get<1>(smallCoord);
    int x = get<0>(position);
    int y = get<1>(position);
    auto result = vector<vector<pixel>>(height);
    for (auto& row : result) {
        row.resize(width);
    }
    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            result[i][j] = big[x + i][y + j];
        }
    }
    return result;
}

void search(string bigImagePath, string smallImagePath, string foundPath) {
    image big = imread(bigImagePath);
    image small = imread(smallImagePath);

    point bigCoord = make_tuple(big.size(), big[0].size());
    point smallCoord = make_tuple(small.size(), small[0].size());

    graph g;

    source_node<point> generateNode(g, generator(bigCoord, smallCoord), false);
    buffer_node<point> subImageBuffer(g);

    function_node<point, tuple<int, point>> diff_node(g, unlimited, [big, small, smallCoord](point position) {
        int height = get<0>(smallCoord);
        int width = get<1>(smallCoord);
        int x = get<0>(position);
        int y = get<1>(position);
        int dist = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                pixel p1 = small[i][j];
                pixel p2 = big[x + i][y + j];
                dist += abs(p1.r - p2.r) + abs(p1.g - p2.g) + abs(p1.b - p2.b);
            }
        }
        return make_tuple(dist, position);
    });

    tuple<int, point> min = make_tuple(INT_MAX, make_tuple(0, 0));
    function_node<tuple<int, point>, tuple<int, point>> minReduceNode(g, 1, minReducer(min));

    make_edge(generateNode, subImageBuffer);
    make_edge(subImageBuffer, diff_node);
    make_edge(diff_node, minReduceNode);

    generateNode.activate();
    g.wait_for_all();

    point position = get<1>(min);

    cout << "Image: " << smallImagePath << endl;
    cout << "Distance: " << get<0>(min) << endl;
    cout << "Coordinates: " << get<0>(position) << ", " << get<1>(position) << endl;
    cout << endl;

    imwrite(subImage(big, position, smallCoord), foundPath);
}

int main() {
    search(dirWithData + "image.dat", dirWithData + "chicken.dat", dirWithData + "chicken_found.dat");
    search(dirWithData + "image.dat", dirWithData + "hat.dat", dirWithData + "hat_found.dat");
    search(dirWithData + "image.dat", dirWithData + "cheer.dat", dirWithData + "cheer_found.dat");
    return 0;
}