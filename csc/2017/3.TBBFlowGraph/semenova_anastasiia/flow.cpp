#include "tbb/flow_graph.h"
#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

using image = vector<vector<pixel>>;

image imread(const std::string &path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        cerr << "Can read only prepared .dat files!" << endl;
        throw invalid_argument(path);
    }
    
    ifstream file(path, ios::binary | ios::in);
    
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
            data[i][j] = pixel { uint8_t(pix[0]), uint8_t(pix[1]), uint8_t(pix[2])};
    }
}

return data;
}

void imwrite(const image& source, const string& path) {
    int h = source.size();
    int w = source[0].size();
    int d = 3;
    ofstream file(path, ios::binary);
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

struct rectangle {
    long start_w;
    long end_w;
    long start_h;
    long end_h;
    
    rectangle() {}
    
    rectangle(long start_w, long start_h, long width, long height) {
        this->start_w = start_w;
        end_w = start_w + width;
        this->start_h  = start_h;
        end_h = start_h + height;
    }
    
    rectangle &operator=(const rectangle &other) {
        start_w = other.start_w;
        end_w = other.end_w;
        start_h = other.start_h;
        end_h = other.end_h;
        return *this;
    }
};


image big_image = imread("data/image.dat");
image small_image;

long min_dif = LONG_MAX;
rectangle min_rectangle;

class generate_rectangles {
    
public:
    generate_rectangles() {
        long width = small_image.size();
        long height = small_image.at(0).size();
        std::cout << "Small image: width = " << width << ", height = " << height << std::endl;
        vector<rectangle> result;
        for (long w = 0; w < big_image.size() - width; ++w) {
            for (long h = 0; h < big_image.at(0).size() - height; ++h) {
                rectangles.push_back(rectangle(w, h, width, height));
            }
        }
        index = 0;
    }
    
    bool operator()(rectangle &result) {
        if (index >= rectangles.size()) {
            return false;
        }
        
        result = rectangles[index];
        index++;
        return true;
    }
    
private:
    std::vector<rectangle> rectangles;
    long index;  
};


std::tuple<rectangle, long> calculate_difference(rectangle const &rect) {
    long difference = 0;
    for (long w = rect.start_w; w < rect.end_w; ++w) {
        for (long h = rect.start_h; h < rect.end_h; ++h) {
            pixel p1 = big_image.at(w).at(h);
            pixel p2 = small_image.at(w - rect.start_w).at(h - rect.start_h);
            difference += abs(p1.b - p2.b) + abs(p2.g - p1.g) + abs(p1.r - p2.r);
        }
    }
    return std::make_tuple(rect, difference);
}

void get_minimum_difference(std::tuple<rectangle,long> const &candidate) {
    if (std::get<1>(candidate) < min_dif) {
        min_dif = std::get<1>(candidate);
        min_rectangle = std::get<0>(candidate);
    }
    
}

image convert_to_image(const rectangle &rect) {
    long width = rect.end_w - rect.start_w;
    long height = rect.end_h - rect.start_h;
    
    std::cout << "Result image: width = " << width << ", height = " << height << std::endl;
    
    image result;
    result.resize(width);
    
    for (vector<pixel> &row : result)
        row.resize(height);
    
    for (long w = 0; w < width; ++w) {
        for (long h = 0; h < height; ++h) {
            result[w][h] = big_image[w + rect.start_w][h + rect.start_h];
        }
    }
    
    return result;
}


void process_image(std::string path) {
    std::cout << "Image: '" << path << ".dat'" << std::endl;
    
    small_image.clear();
    small_image = imread(path + ".dat");
    min_dif = INT_MAX;
    
    graph g;
    
    source_node<rectangle> generateRectangles(g, generate_rectangles(), true);
    buffer_node<rectangle> rectanglesBuffer(g);
    function_node<rectangle, std::tuple<rectangle,long>> calculateDifference(g, unlimited, calculate_difference);
    buffer_node<std::tuple<rectangle,long>> differenceBuffer(g);
    function_node<std::tuple<rectangle,long>> getMinimumDifference(g, unlimited, get_minimum_difference);
    
    make_edge(generateRectangles, rectanglesBuffer);
    make_edge(rectanglesBuffer, calculateDifference);
    make_edge(calculateDifference, differenceBuffer);
    make_edge(differenceBuffer, getMinimumDifference);
    
    g.wait_for_all();
    
    std::cout << "Minimum difference: " << min_dif << std::endl;
    std::cout << "Height borders: "<<  min_rectangle.start_w << " " << min_rectangle.end_w << std::endl;
    std::cout << "Width borders: " << min_rectangle.start_h << " " << min_rectangle.end_h << std::endl;
    
    imwrite(convert_to_image(min_rectangle), path + "_result.dat");
    
    std::cout << "--------------------------------------" << std::endl;
}



int main() {
    std::vector<string> images_to_process;
    images_to_process.push_back("data/cheer");
    images_to_process.push_back("data/chicken");
    images_to_process.push_back("data/hat");
    
    for (auto img : images_to_process) {
        process_image(img);
    }
    
    return 0;
}

