#include <iostream>
#include <fstream>
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

image imread(const std::string &path) {
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
    unsigned long h = source.size();
    unsigned long w = source[0].size();
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

struct image_region {
    int start_x;
    int start_y;
    unsigned long length_x;
    unsigned long length_y;

    image_region() {
    }
};

struct image_splitter {
private:
    image *_img;
    int _cur_x;
    int _cur_y;
    unsigned long _region_length_x;
    unsigned long _region_length_y;
    unsigned long _image_size_x;
    unsigned long _image_size_y;
public:
    image_splitter(image *img, unsigned long length_x, unsigned long length_y) {
        _img = img;
        _cur_x = 0;
        _cur_y = 0;
        _region_length_x = length_x;
        _region_length_y = length_y;
        _image_size_x = img->size();
        _image_size_y = img->front().size();
    }

    bool operator()(image_region &result) {
        if (_cur_x > _image_size_x - _region_length_x)
            return false;

        result.length_x = _region_length_x;
        result.length_y = _region_length_y;
        result.start_x = _cur_x;
        result.start_y = _cur_y;

        _cur_y++;
        if (_cur_y > _image_size_y - _region_length_y) {
            _cur_y = 0;
            _cur_x++;
        }
        return true;
    }
};

//struct region_to_image_comparer {
//private:
//    image *_image_to_search;
//
//    long long diff_between_pixels(const pixel &first, const pixel &second) {
//        return abs(first.r - second.r) + abs(first.g - second.g) + abs(first.b - second.b);
//    }
//
//public:
//    region_to_image_comparer(image *image_to_search) {
//        _image_to_search = image_to_search;
//    }
//
//    tuple<image_region, long long> operator()(const image_region &img_region) {
//        long long diff = 0;
//        unsigned long length_x = _image_to_search->size();
//        unsigned long length_y = _image_to_search->front().size();
//        image img = *img_region.image;
//        if (length_x != img_region.length_x || length_y != img_region.length_y)
//            throw "Image and region size does not match";
//        for (int i = 0; i < length_x; i++) {
//            for (int j = 0; j < length_y; j++) {
//                diff += diff_between_pixels((*_image_to_search)[i][j],
//                                            img[img_region.start_x + i][img_region.start_y + j]);
//            }
//        }
//        return make_tuple(img_region, diff);
//    }
//};

//struct best_region_holder {
//private:
//    long long _min_diff = 10000000;
//public:
//    image_region min_region = image_region();
//
//    void operator()(const tuple<image_region, long long>& region_result) {
//        long long diff = get<1>(region_result);
//        image_region region = get<0>(region_result);
//        if (_min_diff > diff) {
//            _min_diff = diff;
//            min_region = region;
//        }
//    }
//};

long long min_diff = LONG_LONG_MAX;
image_region min_region;

image image_region_to_image(const image& img, const image_region& region) {
    image res = vector<vector<pixel>>(region.length_x);
    for (int i = 0; i < region.length_x; i++) {
        vector<pixel> row(region.length_y);
        for (int j = 0; j < region.length_y; j++) {
            row[j] = img[region.start_x + i][region.start_y + j];
        }
        res[i] = row;
    }
    return res;
}

void find_region_on_image(const string &source_image_path, const string &pattern_to_find_path, const string &result_path) {
    //do not use graph nodes for image reading, to avoid copying of data
    image pattern_image = imread(pattern_to_find_path);
    int pattern_length_x = (int) pattern_image.size();
    int pattern_length_y = (int) pattern_image.front().size();

    image source_image = imread(source_image_path);

    min_diff = LONG_LONG_MAX;
    min_region = image_region();

    graph g;
    source_node<image_region> source_image_splitter(g, image_splitter(&source_image, pattern_image.size(),
                                                                      pattern_image.front().size()), false);
    buffer_node<image_region> regions_buffer_node(g);
    function_node<image_region, tuple<image_region, long long>> diff_getter(g, unlimited,
                    [&](const image_region& region) {
                        long long diff = 0;
                        for (size_t i = 0; i < pattern_length_x; ++i) {
                            for (size_t j = 0; j < pattern_length_y; ++j) {
                                diff += abs(source_image[region.start_x + i][region.start_y + j].r -
                                            pattern_image[i][j].r) +
                                        abs(source_image[region.start_x + i][region.start_y + j].g -
                                            pattern_image[i][j].g) +
                                        abs(source_image[region.start_x + i][region.start_y + j].b -
                                            pattern_image[i][j].b);
                            }
                        }
                        return make_tuple(region, diff);
                    });
    buffer_node<tuple<image_region, long long>> buffer_diff_node(g);
    function_node<tuple<image_region, long long>> min_diff_node(g, serial,
      [&](const tuple<image_region, long long>& region_result) {
            long long diff = get<1>(region_result);
            image_region region = get<0>(region_result);
            if (min_diff > diff) {
                min_diff = diff;
                min_region = region;
            }
        });

    //i decided not to use nodes for image write etc, because info about best image region is stored globally
    //and i do not want to use synthetic limit nodes for collecting all respsonses from min diff node
    make_edge(source_image_splitter, regions_buffer_node);
    make_edge(regions_buffer_node, diff_getter);
    make_edge(diff_getter, buffer_diff_node);
    make_edge(buffer_diff_node, min_diff_node);

    source_image_splitter.activate();
    g.wait_for_all();
    cout << min_region.start_x << " " << min_region.start_y << endl;
    imwrite(image_region_to_image(source_image, min_region), result_path);
}

int main() {
    find_region_on_image("../data/image.dat", "../data/cheer.dat", "../data/cheer_result.dat");
    find_region_on_image("../data/image.dat", "../data/chicken.dat", "../data/chicken_result.dat");
    find_region_on_image("../data/image.dat", "../data/hat.dat", "../data/hat_result.dat");
    return 0;
}