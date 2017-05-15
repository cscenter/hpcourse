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

struct ImageInfo {
    int index;
    int height;
    int width;
};

image imread(const std::string& path) {
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

// Graph node bodies
struct BigImgReaderBody {
    image operator()(string const& path) {
        return imread(path);
    }
};

struct SmallImgReaderBody {
    using input_type = string const&;
    using output_type = tuple<ImageInfo, pair<int, image>>; // pair::first for image index
    using multi_node = multifunction_node<input_type, output_type>;

    SmallImgReaderBody(): image_index(0) {}

    // The same body as that of imread, except that size is forwarded instantly
    void operator()(input_type path, multi_node::output_ports_type &op) {
        if (path.compare(path.size() - 4, 4, ".dat") != 0) {
            cerr << "Can read only prepared .dat files!" << endl;
            throw invalid_argument(path);
        }

        ifstream file(path, ios::binary | ios::in);

        std::uint32_t h, w, d;
        file.read(reinterpret_cast<char*>(&h), 4);
        file.read(reinterpret_cast<char*>(&w), 4);
        file.read(reinterpret_cast<char*>(&d), 4);

        get<0>(op).try_put(ImageInfo{image_index, h, w});

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

        get<1>(op).try_put(pair<int, image>(image_index++, data));
    }

    int image_index;
};

struct FramerBody {
    using input_type = tuple<ImageInfo, image>;
    using output_type = tuple<int, pair<int, int>, image>
    using reciever_type = function_node<input_type, output_type>::successor_type;

    // (Small image info, Big image) -> (Small image id, frames)
    void operator()(input_type& input, reciever_type& rec) {
        auto ii = get<0>(input);
        image& big_image = get<1>(input);
        vector<image> frames;

        for (int origin_h = 0; origin_h + ii.height < big_image.size(); ++origin_h) {
            for (int origin_w = 0; origin_w + ii.width < big_image[0].size(); ++origin_w) {
                image frame(ii.height);
                for (int i = 0; i < ii.height; ++i) {
                    frame[i].resize(ii.width);
                    for (int j = 0; j < ii.width; ++j) {
                        frame[i][j] = big_image[origin_h + i][origin_w + j];
                    }
                }
                rec.try_put(make_tuple(ii.index, make_pair(origin_h, origin_w), frame));
            }
        }
    }
};

struct DiffBody {
    using input_type = tuple<tuple<int, pair<int, int>, image>, image>;
    using output_type = pair<int, int>;

    output_type operator()(input_type& input) {
        auto& frame = get<0>(input);
        int image_index = get<0>(frame);
        auto& origin = get<1>(frame);
        image& frame_img = get<2>(frame);
        image& small_img = get<1>(input);
        int difference = 0;

        for (int i = 0; i < frame_img.size(); ++i) {
            for (int j = 0; j < frame_img[0].size(); ++j) {
                pixel const& p1 = frame_img[i][j];
                pixel const& p2 = small_img[i][j];
                difference += abs(p1.r - p2.r) + abs(p1.g - p2.g) + abs(p1.b - p2.b);
            }
        }
        return make_pair(image_index, difference);
    }
};

struct MinBody {
    int minimum;

    MinBody(): minimum(numeric_limits<int>::max()) {}

    int operator()(int difference) {
        minimum = min(minimum, difference);
        return minimum;
    }
};

struct OutputBody {
    void operator()(int min_diff) {
        cout << min_diff << '\n';
    }
};

int main() {

    vector<string> small_image_paths;
    string big_image_path;

    // Flow graph composition
    graph g;

    // Reads small images and forwards their sizes and matrices do different nodes
    auto max_small_images = unlimited;
    multifunction_node smallreader(g, max_small_images, SmallImgReaderBody());

    // Simply translates .dat to image matrix
    function_node bigreader(g, 1, BigImgReaderBody());

    join_node<FramerBody::input_type, reserving> preframer(g);

    function_node framer(g, unlimited, FramerBody());

    join_node<tuple<pair<int, image>, FramerBody::output_type>, tag_matching>
            prebuff(g,
                    [](pair<int, image>& in)->int {return in.first;},
                    [](FramerBody::output_type& in)->int {return get<0>(in);});

    buffer_node buffer(g);

    function_node diff(g, unlimited, DiffBody());
    overwrite_node min(g);
    continue_node output(g, serial, OutputBody());

    make_edge(output_port<0>(smallreader), input_port<0>(preframer));
    make_edge(output_port<1>(smallreader), input_port<0>(prebuff));
    make_edge(bigreader, input_port<1>(preframer));
    make_edge(preframer, framer);
    make_edge(framer, output_port<1>(prebuff));
    make_edge(prebuff, buffer);
    make_edge(buffer, diff);

    
    // Graph activation

    // Output

    return 0;
}