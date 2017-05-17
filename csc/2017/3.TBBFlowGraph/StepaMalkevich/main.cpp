#include <iostream>
#include <fstream>
#include <array>

#include <tbb/flow_graph.h>
#include <tbb/parallel_for.h>
#include <sstream>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

using image = vector<vector<pixel>>;

struct imagePart {
    uint16_t x_l, y_l, x_r, y_r = 0;

    imagePart() { }

    imagePart(uint16_t th, uint16_t tw, uint16_t bh, uint16_t bw) {
        x_l = th;
        y_l = tw;
        x_r = bh;
        y_r = bw;
    }
};

int differenceBetweenPixels(const pixel &lhs, const pixel &rhs) {
    return abs((int) lhs.r - (int) rhs.r)
           + abs((int) lhs.b - (int) rhs.b)
           + abs((int) lhs.g - (int) rhs.g);
}

image imread(const std::string &path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        cerr << "Can read only prepared .dat files!" << endl;
        throw invalid_argument(path);
    }

    ifstream file(path, ios::binary | ios::in);

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

image getImageRegion(const image &mainImage, const imagePart &smallImage) {
    int64_t h = smallImage.x_r - smallImage.x_l + 1;
    int64_t w = smallImage.y_r - smallImage.y_l + 1;

    auto resImage = vector<vector<pixel>>(h);
    for (auto &row : resImage)
        row.resize(w);

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            resImage[i][j] = mainImage[i + smallImage.x_l][j + smallImage.y_l];
        }
    }

    return resImage;
}

static const image mBigImage = imread("data/image.dat");
static imagePart globalMinImgPart;

class cuttingImage {
    std::list<imagePart> listWithImages;
public:
    cuttingImage(const string &pathToSmallImg) {
        image smallImage = imread(pathToSmallImg);
        const int64_t h_small = smallImage.size();
        const int64_t w_small = smallImage.front().size();
        const int64_t h_big = mBigImage.size();
        const int64_t w_big = mBigImage.front().size();

        for (int64_t i = 0; i < h_big - h_small; ++i) {
            for (int64_t j = 0; j < w_big - w_small; ++j) {
                imagePart r(i, j, i + h_small - 1, j + w_small - 1);
                listWithImages.push_front(r);
            }
        }
    }

    //until listWithImages isn't empty, source_node will get a slice of image
    bool operator()(imagePart &result) {
        if (listWithImages.empty())
            return false;

        imagePart currImgPart = listWithImages.back();
        listWithImages.pop_back();
        result = currImgPart;

        return true;
    }
};

struct difference {
    std::string pathToSmallImage;

    difference(const string &path) : pathToSmallImage(path) { }

    image mSmallImage = imread(pathToSmallImage);

    std::tuple<imagePart, int64_t> operator()(const imagePart &localImgPart) {
        int64_t difference = 0;
        int64_t h = mSmallImage.size();
        int64_t w = mSmallImage.front().size();
        for (int64_t i = 0; i < h; ++i) {
            for (int64_t j = 0; j < w; ++j) {
                difference += differenceBetweenPixels(mBigImage[localImgPart.x_l + i][localImgPart.y_l + j],
                                                      mSmallImage[i][j]);
            }
        }

        return std::make_tuple(localImgPart, difference);
    }
};

struct find_min_diff {
    int64_t minDiff = std::numeric_limits<int64_t>::max();

    imagePart operator()(std::tuple<imagePart, int64_t> tuple) {
        imagePart &tmpImgPart = std::get<0>(tuple);
        int64_t tmpDiff = std::get<1>(tuple);

        if (tmpDiff < minDiff) {
            minDiff = tmpDiff;
            globalMinImgPart = tmpImgPart;
        }

        return globalMinImgPart;
    }
};

class WordDelimitedBySlash : public std::string {
};

std::istream &operator>>(std::istream &is, WordDelimitedBySlash &output) {
    std::getline(is, output, '/');
    return is;
}

std::string getPathResult(const string &smallImgPath) {
    std::istringstream iss(smallImgPath);
    std::vector<std::string> results((std::istream_iterator<WordDelimitedBySlash>(iss)),
                                     std::istream_iterator<WordDelimitedBySlash>());

    std::string pathResult = "";
    for (int i = 0; i < results.size() - 1; ++i) {
        pathResult += results[i] + "/";
    }

    pathResult += "result_for_" + results[results.size() - 1];

    return pathResult;
}

void solve(const string &smallImgPath) {
    graph g;
    source_node<imagePart> cutBigImageToParts(g, cuttingImage(smallImgPath), false);

    //the buffer_node will ask the previous node(source_node) elements until he have them
    buffer_node<imagePart> partsBuffer(g);
    //the functional_node works ||(unlimited flag) with all imageMatrix from the previous node
    function_node<imagePart, std::tuple<imagePart, int64_t>> getDifference(g, unlimited, difference(smallImgPath));
    //the buffer_node to ensure that the items did not disappear
    buffer_node<std::tuple<imagePart, int64_t>> diffBuffer(g);
    //the functional_node - serial node to find the minimum element(min difference)
    function_node<std::tuple<imagePart, int64_t>> findMinDifference(g, 1, find_min_diff());

    //create edges
    make_edge(cutBigImageToParts, partsBuffer);
    make_edge(partsBuffer, getDifference);
    make_edge(getDifference, diffBuffer);
    make_edge(diffBuffer, findMinDifference);

    //activate first node for begin cutting elements
    cutBigImageToParts.activate();
    g.wait_for_all();

    //save the result to file
    image resultImage = getImageRegion(mBigImage, globalMinImgPart);
    std::string pathResult = getPathResult(smallImgPath);
    imwrite(resultImage, pathResult);
}


int main() {
    solve("data/hat.dat");
    solve("data/chicken.dat");
    solve("data/cheer.dat");
    return 0;

}