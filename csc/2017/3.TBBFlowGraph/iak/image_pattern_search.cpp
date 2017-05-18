#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

#include <tbb/flow_graph.h>
#include <cassert>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

using image = vector<vector<pixel>>;

/*class ImageViewer {
public:
    ImageViewer(image* source, size_t h, size_t w) :
            ptr(source) {}

    vector<pixel>& operator[](size_t i) {
        return (*ptr)[i];
    }

    size_t size() const {
        return ptr->size();
    }

private:
    image* ptr;
    int height;
    int width;
};*/

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
    using input_type = string;
    using output_type = image;

    image operator()(input_type path) {
        return imread(path);
    }
};

struct SmallImgReaderBody {
    using input_type = string;
    using imginfo_type = ImageInfo;
    using indexed_image_type = pair<int, image>;
    using output_type = tuple<imginfo_type, indexed_image_type>;

    using multi_node = multifunction_node<input_type, output_type>;

    // The same body as that of imread, except that size is forwarded instantly
    void operator()(input_type path, multi_node::output_ports_type &op) {
        if (path.compare(path.size() - 4, 4, ".dat") != 0) {
            cerr << "Can read only prepared .dat files!" << endl;
            throw invalid_argument(path);
        }

        int image_index = n_images++;

        ifstream file(path, ios::binary | ios::in);

        std::uint32_t h, w, d;
        file.read(reinterpret_cast<char*>(&h), 4);
        file.read(reinterpret_cast<char*>(&w), 4);
        file.read(reinterpret_cast<char*>(&d), 4);

        get<0>(op).try_put(imginfo_type{
                image_index,
                static_cast<int>(h),
                static_cast<int>(w)});

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

        get<1>(op).try_put(indexed_image_type{image_index, data});
    }

    // If the body is going to be copied, the global numbering won't suffer
    static int n_images;
};

int SmallImgReaderBody::n_images = 0;

struct FramerBody {
    using input_type = tuple<ImageInfo, image>;
    using origin_coord_type = pair<int, int>;
    using output_type = tuple<int, origin_coord_type, image>;
    using reciever_type = typename function_node<input_type, output_type>::successor_type;

    // (Small image info, Big image) -> (Small image id, frames)
    void operator()(input_type& input, reciever_type& rec) {
        ImageInfo ii = get<0>(input);
        image& big_image = get<1>(input);

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
    using framer_output = FramerBody::output_type;
    using input_type = tuple<framer_output, image>;
    using origin_coord_type = typename tuple_element<1, framer_output>::type;
    using output_type = pair<origin_coord_type, int>;

    using multi_node = multifunction_node<input_type, tuple<output_type, output_type, output_type>>;

    void operator()(input_type& input, multi_node::output_ports_type& op) {
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

        // template parameter must be known at compile-time :(
        output_type task = make_pair(origin, difference);
        switch (image_index) {
            case 0:
                get<0>(op).try_put(task);
                break;
            case 1:
                get<1>(op).try_put(task);
                break;
            case 2:
                get<2>(op).try_put(task);
                break;
            default:
                abort();
        }
    }
};

struct MinBody {

    using input_type = DiffBody::output_type;
    using output_type = input_type::first_type;

    MinBody(): minimum(numeric_limits<int>::max()) {}

    output_type operator()(input_type& difference) {
        if (difference.second < minimum) {
            minimum = difference.second;
            origin = difference.first;
        }
        return origin;
    }

    int minimum;
    output_type origin;
};

int main() {

    vector<string> small_image_paths = {
            "data/hat.dat",
            "data/chicken.dat",
            "data/cheer.dat"
    };
    string big_image_path = "image.dat";

    // Flow graph composition
    graph g;

    // Reads small images and forwards their sizes and matrices do different nodes
    size_t n_small_images = 3;
    multifunction_node<SmallImgReaderBody::input_type, SmallImgReaderBody::output_type>
            smallreader(g, n_small_images, SmallImgReaderBody());

    // Simply translates .dat to image matrix
    function_node<string, image> bigreader(g, 1, BigImgReaderBody());

    join_node<FramerBody::input_type, reserving> preframer(g);

    function_node<FramerBody::input_type, FramerBody::output_type>
            framer(g, unlimited, FramerBody());

    // Join small image with a frame form big image
    using buffer_element = tuple<SmallImgReaderBody::indexed_image_type, FramerBody::output_type>;
    join_node<buffer_element, tag_matching> prebuff(
            g,
            [](pair<int, image>& in)->int {return in.first;},
            [](FramerBody::output_type& in)->int {return get<0>(in);}
    );

    buffer_node<buffer_element> buffer(g);

    multifunction_node<DiffBody::input_type, DiffBody::output_type> diff(g, unlimited, DiffBody());

    vector<function_node<DiffBody::output_type>> min(
            n_small_images,
            function_node<DiffBody::output_type>(g, 1, MinBody())
    );

    using coords_type = pair<int, int>;
    vector<overwrite_node<coords_type>> output(n_small_images, overwrite_node<coords_type>(g));

    make_edge(output_port<0>(smallreader), input_port<0>(preframer));
    make_edge(output_port<1>(smallreader), input_port<0>(prebuff));
    make_edge(bigreader, input_port<1>(preframer));
    make_edge(preframer, framer);
    make_edge(framer, input_port<1>(prebuff));
    make_edge(prebuff, buffer);
    make_edge(buffer, diff);
//    buffer.register_successor(diff);
    make_edge(output_port<0>(diff), min[0]);
    make_edge(output_port<1>(diff), min[1]);
    make_edge(output_port<2>(diff), min[2]);
    for (size_t i = 0; i < n_small_images; ++i) {
        make_edge(min[i], output[i]);
    }

    // Graph activation
    bigreader.try_put(big_image_path);
    for (auto& s: small_image_paths) {
        smallreader.try_put(s);
    }
    g.wait_for_all();

    // Output
    for (size_t i = 0; i < n_small_images; ++i) {
        coords_type origin;
        output[i].try_get(origin);
        cout << "Image " << i << " found at (" << origin.first << ", " << origin.second << ")\n";
    }

    return 0;
}