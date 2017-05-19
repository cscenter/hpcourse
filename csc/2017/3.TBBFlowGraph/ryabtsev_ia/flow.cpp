#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

#define maxx numeric_limits<int64_t>::max()

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

int32_t findDifference(pixel first, pixel second) {
    return abs(first.r-second.r) + abs(first.g-second.g) + abs(first.b-second.b);
} 

class Rectangle {
    size_t tleft_x = 0;
    size_t tleft_y = 0;
    size_t bright_x = 0;
    size_t bright_y = 0;
public:
    Rectangle(){};
    Rectangle(size_t tlx, size_t tly, size_t brx, size_t bry) : tleft_x(tlx), tleft_y(tly), bright_x(brx), bright_y(bry) {};
    size_t getTopLeftX() const {
        return tleft_x;
    }
    size_t getTopLeftY() const {
        return tleft_y;
    }
    size_t getBottomRightX() const {
        return bright_x;
    }
    size_t getBottomRightY() const {
        return bright_y;
    }
};

class ImageSlices {
    queue<Rectangle> images;
public:
    ImageSlices(image picture, image subpicture) {
        size_t picture_y = picture.size();
        size_t picture_x = picture.front().size();
        size_t subpicture_y = subpicture.size();
        size_t subpicture_x = subpicture.front().size();
        //make all rectangles with necessary size
        for (size_t x = 0; x != picture_x - subpicture_x; ++x) {
            for (size_t y = 0; y != picture_y - subpicture_y; ++y) {
                images.push(Rectangle(x, y, x + subpicture_x - 1, y + subpicture_y - 1));
            }
        }
    };
    bool operator()(Rectangle &rect) {
        if (images.empty())
            return false;
        rect= images.front();
        images.pop();
        return true;
    }
};

class DifferenceHandler {
    image picture;
    image subpicture;
public:
    DifferenceHandler(image picture, image subpicture): picture(picture), subpicture(subpicture) {};
    pair<Rectangle, int64_t> operator()(const Rectangle &rectIm) {
        int64_t diff = 0;
        for (size_t x = 0; x != subpicture.front().size(); ++x) {
            for (size_t y = 0; y != subpicture.size(); ++y) {
                diff += findDifference(picture[rectIm.getTopLeftY() + y][rectIm.getTopLeftX() + x],subpicture[y][x]);
            }
        }
        return {rectIm, diff};
    }
};

void printInfo(Rectangle closestRectangle) {
    cout << "Top left x coordinate = " << closestRectangle.getTopLeftX()<< "\nTop left y coordinate = " << closestRectangle.getTopLeftY() 
    << "\nBottom right x coordinate = " << closestRectangle.getBottomRightX() << "\nBottom right y coordinate = " << closestRectangle.getBottomRightY() << "\n";
}

void writeEntranceImage(Rectangle closestRectangle, string entranceImagePath, image picture) {
    image occurenceImage(closestRectangle.getBottomRightY() - closestRectangle.getTopLeftY() +1, 
        vector<pixel>(closestRectangle.getBottomRightX() - closestRectangle.getTopLeftX() + 1));

    for (size_t x = 0; x != closestRectangle.getBottomRightX() - closestRectangle.getTopLeftX() + 1; ++x) {
        for (size_t y = 0; y != closestRectangle.getBottomRightY() - closestRectangle.getTopLeftY() +1; ++y) {
            occurenceImage[y][x] = picture[y + closestRectangle.getTopLeftY()][x + closestRectangle.getTopLeftX()];
        }
    }
    imwrite(occurenceImage, entranceImagePath);
}


void checkPictures(string subpicturePath, string entranceImagePath, image picture) {

    image subpicture = imread(subpicturePath);
    //our graph
    graph g;

    // generate rectangles
    source_node<Rectangle> generateRectanglesNode(g, ImageSlices(picture, subpicture), true);
    // buffer
    buffer_node<Rectangle> slicesBufferNode(g);
    //functional node for finding the differences
    function_node<Rectangle, pair<Rectangle, int64_t>> differenceNode(g, unlimited, 
        DifferenceHandler(picture, subpicture));
    //difference buffe
    buffer_node<pair<Rectangle, int64_t>> differenceBufferNode(g);
    

    //find the minimum difference
    int64_t min = maxx;
    Rectangle closestRectangle;

    function_node<pair<Rectangle, int64_t>> findMinNode(g, 1, [&](pair<Rectangle, int64_t> t) {
        Rectangle &rect = t.first;
        int64_t currMin = t.second;
        if (currMin < min) {
            min = currMin;
            closestRectangle = rect;
        }
    });

    make_edge(generateRectanglesNode, slicesBufferNode);
    make_edge(slicesBufferNode, differenceNode);
    make_edge(differenceNode, differenceBufferNode);
    make_edge(differenceBufferNode, findMinNode);
    g.wait_for_all();

    printInfo(closestRectangle);
    writeEntranceImage(closestRectangle, entranceImagePath, picture);
}


int main() {
    //big image
    image picture = imread("../data/image.dat");
    checkPictures("../data/hat.dat", "../data/hat_1.dat", picture);
    checkPictures("../data/chicken.dat", "../data/chicken_1.dat", picture);
    checkPictures("../data/cheer.dat", "../data/cheer_1.dat", picture);
    return 0;
}