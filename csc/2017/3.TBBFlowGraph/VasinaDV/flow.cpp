#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

struct pixel
{
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

struct rectangle
{
    uint16_t tlx, tly, brx, bry;

    rectangle() : tlx(0), tly(0), brx(0), bry(0)
    {}

    rectangle(uint16_t tlx, uint16_t tly, uint16_t brx, uint16_t bry)
            : tlx(tlx), tly(tly), brx(brx), bry(bry)
    {}
};

using image = vector<vector<pixel>>;

image imread(const string& path) {
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

const image big_img = imread("./data/image.dat");
rectangle min_rectangle;

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

class im_split_into_rectangles
{
public:
    im_split_into_rectangles(const string &path)
    {
        image small_img = imread(path);

        const int64_t s_img_h = small_img.size();
        const int64_t b_img_h = big_img.size();

        const int64_t s_img_w = small_img.front().size();
        const int64_t b_img_w = big_img.front().size();

        for (int64_t i = 0; i < b_img_h - s_img_h; ++i)
        {
            for (int64_t j = 0; j < b_img_w - s_img_w; ++j)
            {
                rectangles.push(rectangle(i, j, i + s_img_h - 1, j + s_img_w - 1));
            }
        }
    }

    bool operator()(rectangle& rect)
    {
        if (!rectangles.empty())
        {
            rect = rectangles.front();
            rectangles.pop();
            return true;
        }
        return false;
    }

private:
    queue<rectangle> rectangles;
};

class im_find_difference
{
public:
    im_find_difference(const string& path)
    {
        small_img = imread(path);
    }

    tuple<rectangle, int64_t> operator()(const rectangle& rect)
    {
        int64_t difference = 0;
        int64_t h = small_img.size();
        int64_t w = small_img.front().size();

        for (int64_t i = 0; i < h; ++i)
        {
            for (int64_t j = 0; j < w; ++j)
            {
                pixel p1 = big_img[rect.tlx + i][rect.tly + j];
                pixel p2 = small_img[i][j];
                difference += abs(p1.r - p2.r) + abs(p1.g - p2.g) + abs(p1.b - p2.b);
            }
        }

        return make_tuple(rect, difference);
    }

    image small_img;
};

class im_find_min_difference
{
public:
    int64_t min_difference = numeric_limits<int64_t>::max();

    rectangle operator()(const tuple<rectangle, int64_t>& var)
    {
        if (get<1>(var) < min_difference) {
            min_difference = get<1>(var);
            min_rectangle = get<0>(var);
        }
        return min_rectangle;
    }
};

image imconvert(const rectangle& rect)
{
    int64_t height = abs(rect.brx - rect.tlx) + 1;
    int64_t width = abs(rect.bry - rect.tly) + 1;

    image result = vector<vector<pixel>>(height);

    for (auto &row : result)
        row.resize(width);

    for (long i = 0; i < height; ++i)
    {
        for (long j = 0; j < width; ++j)
        {
            result[i][j] = big_img[i + rect.tlx][j + rect.tly];
        }
    }

    return result;
}

void improcess(const string& path, const string& counter)
{
    graph g;

    source_node<rectangle> split_into_rectangles(g, im_split_into_rectangles(path), false);
    buffer_node<rectangle> rectangles_buffer(g);
    function_node<rectangle, tuple<rectangle, int64_t>> get_difference(g, unlimited, im_find_difference(path));
    buffer_node<tuple<rectangle, int64_t>> differences_buffer(g);
    function_node<tuple<rectangle, int64_t>> get_min_difference(g, 1, im_find_min_difference());

    make_edge(split_into_rectangles, rectangles_buffer);
    make_edge(rectangles_buffer, get_difference);
    make_edge(get_difference, differences_buffer);
    make_edge(differences_buffer, get_min_difference);

    split_into_rectangles.activate();
    g.wait_for_all();

    imwrite(imconvert(min_rectangle), "./data/result" + counter + ".dat");
}

int main()
{
    improcess("./data/hat.dat", "1");
    improcess("./data/chicken.dat", "2");
    improcess("./data/cheer.dat", "3");
    return 0;
}
