#include <iostream>
#include <fstream>

#include <tbb/flow_graph.h>
#include <tbb/concurrent_unordered_map.h>
#include <tbb/concurrent_unordered_set.h>
#include <assert.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;

    size_t operator- (const pixel &other) const {
        // squared Euclidean distance
        return (r - other.r) * (r - other.r) +
               (g - other.g) * (g - other.g) +
               (b - other.b) * (b - other.b);
    }
};

using Image = vector<vector<pixel>>;

Image imread(const std::string& path) {
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

    auto data = Image(h);
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

void imwrite(const Image& source, const string& path) {
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

struct fragment_meta {
    string target_image_id;
    size_t position_x;
    size_t position_y;
    size_t height;
    size_t width;
    size_t total_fragments_count;

    friend std::ostream &operator<<(std::ostream &os, const fragment_meta &meta)
    {
        os << "target image: " << meta.target_image_id << endl;
        os << "position x: " << meta.position_x << "; y: " << meta.position_y << endl;
        os << "size h: " << meta.height << "; w: " << meta.width << endl;
        return os;
    }
};

struct Fragment {
    fragment_meta meta;
    const Image* target_image;
    const Image* orig_image;

    Fragment() : target_image(nullptr), orig_image(nullptr)
    {}

    Fragment(const fragment_meta &meta, const Image* target_image, const Image* orig_image) :
            meta(meta),
            target_image(target_image),
            orig_image(orig_image)
    {
        assert(target_image->size() == meta.height);
        assert(target_image->front().size() == meta.width);
    }

    size_t calc_difference() const {
        size_t result = 0;
        for (size_t i = 0; i < meta.height; ++i)
        {
            for (size_t j = 0; j < meta.width; ++j)
            {
                result += (*target_image)[i][j] - (*orig_image)[meta.position_x + i][meta.position_y + j];
            }
        }
        return result;
    }
};


struct image_reader_body {
    tbb::concurrent_unordered_set<string> uniq_images;

    tuple<string, Image*> operator() (const std::string &path) {
        if (uniq_images.count(path) > 0) {
            return make_tuple(path, nullptr);
        }
        uniq_images.insert(path);
        return make_tuple(path, new Image(std::move(imread(path))));
    }
};

struct fragmentation_body {
    Image orig_image;

    fragmentation_body(const Image &orig_image) :
            orig_image(orig_image)
    {}

    void operator() (const tuple<string, Image*> &image, multifunction_node<tuple<string, Image*>, tuple<Fragment>>::output_ports_type &op) const {
        Image* img = get<1>(image);

        if (!img)
        { return; }

        size_t height = img->size();
        size_t width = img->front().size();

        size_t orig_height = orig_image.size();
        size_t orig_width = orig_image.front().size();

        size_t total_fragments_count = (orig_height - height) * (orig_width - width);

        for (size_t i = orig_height - height; i != 0 ; --i)
        {
            for (size_t j = orig_width - width; j != 0; --j)
            {
                get<0>(op).try_put(Fragment(fragment_meta {get<0>(image), i, j, height, width, total_fragments_count}, img, &orig_image));
            }
        }
    }
};

struct reducer_body {
    // map image_id to pair of fragment meta with minimum difference and minimum difference itself
    tbb::concurrent_unordered_map<string, pair<Fragment, size_t>> min_values;
    tbb::concurrent_unordered_map<string, tbb::atomic<size_t>> counters;

    Fragment operator() (const pair<Fragment, size_t> &candidate) {

        auto last_value = min_values[candidate.first.meta.target_image_id];

        if (last_value.first.meta.target_image_id.empty() || candidate.second < last_value.second) {
            // overwrite value if it's the first value or difference less than existing
            min_values[candidate.first.meta.target_image_id] = candidate;
        }

        size_t counter = counters[candidate.first.meta.target_image_id].fetch_and_increment();

        if (counter + 1 == last_value.first.meta.total_fragments_count) {
            return last_value.first;
        }
        return Fragment();
    }
};

void build_and_run(const string &original_image_path, const vector<string> &target_image_paths) {

    const Image original_image = imread(original_image_path);

    graph g;

    // gets a path to the image and returns the path (as the identifier) and the image
    function_node<std::string, tuple<string, Image*>> image_reader(g, unlimited, image_reader_body());

    // gets the image with identifier and returns fragments for comparison
    // a multifunction node allows to put a values many times
    multifunction_node<tuple<string, Image*>, tuple<Fragment>> fragmentation_node(g, unlimited, fragmentation_body(original_image));

    buffer_node<Fragment> fragments_buffer(g);

    // returns a fragment meta with calculated difference between the small image and the chunk of the original image
    function_node<Fragment, pair<Fragment, size_t>> subtractor(g, unlimited, [](const Fragment &fragment) {
        return make_pair(fragment, fragment.calc_difference());
    });

    // returns a meta info about fragment that has the smallest difference or nullptr if processing isn't finished
    function_node<pair<Fragment, size_t>, Fragment> reducer(g, unlimited, reducer_body());

    broadcast_node<Fragment> fragment_broadcaster(g);

    function_node<Fragment> image_writer(g, unlimited, [&original_image](const Fragment &fragment) {
        auto meta = fragment.meta;
        if (!meta.target_image_id.empty()) {
            size_t vicinity_size = min(meta.height, meta.width);
            size_t start_x = meta.position_x > vicinity_size ? meta.position_x - vicinity_size : 0;
            size_t start_y = meta.position_y > vicinity_size ? meta.position_y - vicinity_size : 0;
            size_t end_x = std::min(meta.position_x + meta.height + vicinity_size, original_image.size());
            size_t end_y = std::min(meta.position_y + meta.width + vicinity_size, original_image.front().size());


            auto data = Image(end_x - start_x);
            for (auto& row: data) {
                row.resize(end_y - start_y);
            }

            for (size_t i = start_x; i < end_x; ++i) {
                for (size_t j = start_y; j < end_y; ++j) {
                    data[i - start_x][j - start_y] = original_image[i][j];
                }
            }

            imwrite(data, meta.target_image_id.substr(0, meta.target_image_id.size() - 4) + "__result.dat");
            cout << meta << endl;
        }
    });

    function_node<Fragment> image_deleter(g, unlimited, [](const Fragment &fragment) {
        if (fragment.target_image) {
            delete fragment.target_image;
        }
    });

    make_edge(image_reader, fragmentation_node);
    make_edge(output_port<0>(fragmentation_node), fragments_buffer);
    make_edge(output_port<0>(fragmentation_node), subtractor);
    make_edge(subtractor, reducer);
    make_edge(reducer, fragment_broadcaster);
    make_edge(fragment_broadcaster, image_writer);
    make_edge(fragment_broadcaster, image_deleter);

    for (auto && small_img : target_image_paths) {
        image_reader.try_put(small_img);
    }

    g.wait_for_all();
}

int main(int argc, char *argv[]) {
    string images_dir = "data/";

    if (argc > 1) {
        images_dir = argv[1];
        if (images_dir.back() != '/') {
            images_dir += '/';
        }
    }

    vector<string> small_images = {
            images_dir + "hat.dat",
            images_dir + "hat.dat",
            images_dir + "hat.dat",
            images_dir + "hat.dat",
            images_dir + "cheer.dat",
            images_dir + "cheer.dat",
            images_dir + "cheer.dat",
            images_dir + "chicken.dat",
            images_dir + "chicken.dat",
            images_dir + "chicken.dat"
    };

    build_and_run(images_dir + "image.dat", small_images);

    return 0;
}