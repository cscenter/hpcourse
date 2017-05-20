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


using coords = pair<size_t, size_t>;

class MyFlowGraph: public graph {
public:
    MyFlowGraph(image const& big_image, string const& small_image_path):
            graph(),

            small_image_name(small_image_path),
            small_image(imread(small_image_path)),
            big_image(big_image),

            framer(*this, FramerBody(this->small_image, this->big_image)),
            frame_buffer(*this),
            diff(*this, unlimited, DiffBody(this->small_image, this->big_image)),
            diff_buffer(*this),
            min(*this, serial, MinBody())
    {
        make_edge(framer, frame_buffer);
        make_edge(frame_buffer, diff);
        make_edge(diff, diff_buffer);
        make_edge(diff_buffer, min);
    }

    void print_result(ostream& os) {
        MinBody result = min.copy_function_object<MinBody>();
        coords const& origin = result.origin;
        os << "Image " << small_image_name << " found at (" << origin.first << ", " << origin.second << ")"
                " (diff = " << result.minimum << ")\n";
    }

private:

    string small_image_name;
    image small_image;
    image const& big_image;

    source_node<coords> framer;

    buffer_node<coords> frame_buffer;

    function_node<coords, pair<coords, int>> diff;

    buffer_node<pair<coords, int>> diff_buffer;

    function_node<pair<coords, int>> min;

    // Graph node bodies

    struct FramerBody {
        using output_type = coords;

        FramerBody(image const& small_image, image const& big_image) :
                originH(0),
                originW(0),
                smallH(small_image.size()),
                smallW(small_image[0].size()),
                bigH(big_image.size()),
                bigW(big_image[0].size())
        {}

        // (Small image info, Big image) -> (Small image id, frames)
        bool operator()(output_type& origin) {

            assert(originH + smallH <= bigH && originW + smallW <= bigW);

            origin = make_pair(originH, originW);

            // Go to the next frame origin
            ++originW;
            if (originW + smallW == bigW + 1) {
                originW = 0;
                ++originH;
            }
            return originH + smallH <= bigH;
        }

        size_t originH;
        size_t originW;

        size_t smallH;
        size_t smallW;

        size_t bigH;
        size_t bigW;
    };

    struct DiffBody {
        using input_type = coords;
        using output_type = pair<coords, int>;

        DiffBody(image const& small_image, image const& big_image) :
            small_image(small_image),
            big_image(big_image)
        {}

        output_type operator()(input_type const& origin) {

            int difference = 0;
            int h = small_image.size();
            int w = small_image[0].size();
            for (int i = 0; i < h; ++i) {
                for (int j = 0; j < w; ++j) {
                    pixel const &p1 = big_image[origin.first + i][origin.second + j];
                    pixel const &p2 = small_image[i][j];
                    difference += abs((int)p1.r - (int)p2.r)
                                  + abs((int)p1.g - (int)p2.g)
                                  + abs((int)p1.b - (int)p2.b);
                }
            }

            return make_pair(origin, difference);
        }

        image const& small_image;
        image const& big_image;
    };

    struct MinBody {

        using input_type = pair<coords, int>;

        MinBody(): minimum(numeric_limits<int>::max()) {}

        void operator()(input_type const& difference) {
            if (difference.second < minimum) {
                minimum = difference.second;
                origin = difference.first;
            }
        }

        int minimum;
        coords origin;
    };
};

int main() {

    vector<string> small_image_paths = {
            "../../data/hat.dat",
            "../../data/chicken.dat",
            "../../data/cheer.dat"
    };

    image big_image = imread("../../data/image.dat");

    // Graph activation
    MyFlowGraph g0(big_image, small_image_paths[0]);
    MyFlowGraph g1(big_image, small_image_paths[1]);
    MyFlowGraph g2(big_image, small_image_paths[2]);

    // Output
    g0.wait_for_all();
    g1.wait_for_all();
    g2.wait_for_all();

    g0.print_result(cout);
    g1.print_result(cout);
    g2.print_result(cout);

    return 0;
}