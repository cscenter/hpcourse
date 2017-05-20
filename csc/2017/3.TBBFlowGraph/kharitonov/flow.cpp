#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <limits>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

struct Pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
    int GetDiff(const Pixel &other) { return abs(r - other.r) + abs(g - other.g) + abs(b - other.b);}
};

struct Point {
    Point() : x(0), y(0) {}
    Point(int x, int y) : x(x), y(y) {}
    int x, y;
    Point operator+(const Point &other) { return Point(x + other.x, y + other.y); }
    Point &operator+=(const Point &other) { x += other.x; y += other.y; return *this; }
};

typedef Point Vec2d;

struct Rectangle {
    Rectangle() {}
    Rectangle(const Point &p1, const Point &p2) : p1(p1), p2(p2) {}
    Point p1, p2;
    Rectangle operator+(const Vec2d& vec) { return Rectangle(p1 + vec, p2 + vec); }
    Rectangle &operator+=(const Vec2d &vec) { p1 += vec; p2 += vec; return *this; }
};

ostream & operator<<(ostream &s, Point const &p) {
    s << "(" << p.x << ", " << p.y << ")";
    return s;
}

ostream & operator<<(ostream &s, Rectangle const &r) {
    s << "Rectangle(p1 = " << r.p1 << ", p2 = " << r.p2 << ")";
    return s;
}

using image = vector<vector<Pixel>>;

struct RectangleGenerator {
    RectangleGenerator(const image &src, const image &pattern)
        : current(Point(0, 0), Point(pattern.back().size() - 1, pattern.size() - 1))
        , srcRigthBottomPoint(Point(src.back().size(), src.size()))
    {}

    bool operator()(Rectangle &rect) {
        if (current.p2.y >= srcRigthBottomPoint.y)
            return false;

        rect = current;
        current += Point(STEP, 0);
        if (current.p2.x >= srcRigthBottomPoint.x) {
            current += Point(-current.p1.x, STEP);
        }
        return true;
    }

private:
    Rectangle current;
    Point srcRigthBottomPoint;
    static constexpr int STEP = 1;
};

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

    auto data = vector<vector<Pixel>>(h);
    for (auto& row: data) {
        row.resize(w);
    }

    for (size_t i = 0; i < h; ++i) {
        for (size_t j = 0; j < w; ++j) {
            auto pix = array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = Pixel { uint8_t(pix[0]),
                                 uint8_t(pix[1]),
                                 uint8_t(pix[2])};
        }
    }

    return data;
}

void imwrite(const image& source, const string& path) {
    size_t h = source.size();
    size_t w = source[0].size();
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

void go(const std::string &inFileName, const std::string &pattern, const std::string &outFileName) {
    image InputImg = imread(inFileName);
    image patternImg = imread(pattern);

    graph g;

    source_node<Rectangle> rectangleGenerator(g, RectangleGenerator(InputImg, patternImg),false);

    buffer_node<Rectangle> rectangleBuffer(g);

    function_node<Rectangle, std::pair<Rectangle, int> > diffCalculator(g, unlimited, [&InputImg, &patternImg](Rectangle rect) {
        int diff = 0;
        for (size_t y = 0; y < patternImg.size(); ++y) {
            for (size_t x = 0; x < patternImg.back().size(); ++x)
                diff += InputImg[rect.p1.y + y][rect.p1.x + x].GetDiff(patternImg[y][x]);
        }
        return make_pair(rect, diff);
    });

    buffer_node<pair<Rectangle, int>> diffBuffer(g);

    pair<Rectangle, int> reduceResult(Rectangle(), numeric_limits<int>::max());
    function_node<pair<Rectangle, int>> reducerNode(g, 1, [&reduceResult](pair<Rectangle, int> value) {
        reduceResult = min(reduceResult, value, [](const pair<Rectangle, int> &l, const pair<Rectangle, int> &r) { return l.second < r.second; });
    });

    make_edge(rectangleGenerator, rectangleBuffer);
    make_edge(rectangleBuffer, diffCalculator);
    make_edge(diffCalculator, diffBuffer);
    make_edge(diffBuffer, reducerNode);

    rectangleGenerator.activate();
    g.wait_for_all();

    Rectangle result = reduceResult.first;
    cout << "Diff:" << reduceResult.second << ", Rectangle: " << result << endl;

    size_t w = result.p2.x - result.p1.x;
    size_t h = result.p2.y - result.p1.y;
    image outImg(h, vector<Pixel>(w, {0, 0, 0}));
    for (size_t y = 0; y < h; ++y) {
        for (size_t x = 0; x < w; ++x) {
            outImg[y][x] = InputImg[y + result.p1.y][x + result.p1.x];
        }
    }
    imwrite(outImg, outFileName);
}

const string path       = "../../data/";
const string srcImg     = path + "image.dat";
const string hatImg     = path + "hat.dat";
const string chickenImg = path + "chicken.dat";
const string cheerImg   = path + "cheer.dat";


int main()
{
    go(srcImg, hatImg,      hatImg + "out.dat");
    go(srcImg, chickenImg,  chickenImg + "out.dat");
    go(srcImg, cheerImg,    cheerImg + "out.dat");
    return 0;
}
