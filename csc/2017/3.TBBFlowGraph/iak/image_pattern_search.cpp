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
    MyFlowGraph(string const& big_image_path, string const& small_image_path):
            graph(),
            small_image_name(small_image_path),
            small_image_reader(*this, ReaderBody(small_image_path)),
            framer(*this, FramerBody(small_image_path, big_image_path)),
            buffer(*this),
            prediff(*this),
            diff(*this, unlimited, DiffBody()),
            min(*this, unlimited, MinBody())
    {
        make_edge(small_image_reader, input_port<0>(prediff));
        make_edge(framer, buffer);
        make_edge(buffer, input_port<1>(prediff));
        make_edge(prediff, diff);
        make_edge(diff, min);
    }

    void print_result(ostream& os) {
        coords origin = min.copy_function_object<MinBody>().origin;
        os << "Image " << small_image_name << " found at (" << origin.first << ", " << origin.second << ")\n";
    }

private:

    string small_image_name;

    source_node<image> small_image_reader;
    source_node<tuple<coords, image>> framer;

    using image_pair = tuple<image, image>;
    buffer_node<tuple<coords, image>> buffer;

    join_node<tuple<image, tuple<coords, image>>> prediff;
    function_node<tuple<image, tuple<coords, image>>, pair<coords, int>> diff;

    function_node<pair<coords, int>> min;

    // Graph node bodies

    struct ReaderBody {
        using input_type = string;
        using output_type = image;

        string path;

        ReaderBody(string const& path): path(path)
        {}

        bool operator()(output_type& img) {
            img = imread(path);
            return false;
        }
    };

    struct FramerBody {
        using output_type = tuple<coords, image>;

        FramerBody(string const& small_image_path, string const& big_image_path) :
                originH(0),
                originW(0)
        {
            big_image = imread(big_image_path);

            if (small_image_path.compare(small_image_path.size() - 4, 4, ".dat") != 0) {
                cerr << "Can read only prepared .dat files!" << endl;
                throw invalid_argument(small_image_path);
            }

            ifstream small_image_file(small_image_path,  ios::binary | ios::in);

            std::uint32_t h, w;
            small_image_file.read(reinterpret_cast<char*>(&h), 4);
            small_image_file.read(reinterpret_cast<char*>(&w), 4);

            small_image_file.close();

            smallH = h;
            smallW = w;
        }

        // (Small image info, Big image) -> (Small image id, frames)
        bool operator()(output_type& coords_and_frame) {

            size_t bigH = big_image.size();
            size_t bigW = big_image[0].size();
            assert(originH + smallH <= bigH && originW + smallW <= bigW);

            get<0>(coords_and_frame) = make_pair(originH, originW);
            image& frame = get<1>(coords_and_frame);

            frame.resize(smallH);
            for (size_t i = 0; i < smallH; ++i) {
                frame[i].resize(smallW);
                for (size_t j = 0; j < smallW; ++j) {
                    frame[i][j] = big_image[originH + i][originW + j];
                }
            }

            // Go to the next frame origin
            if (originH + smallH == bigH && originW + smallW == bigW)
                return false;
            else {
                if (++originW + smallW == bigH + 1) {
                    originW = 0;
                    ++originH;
                }
                return true;
            }
        }

        size_t originH;
        size_t originW;

        size_t smallH;
        size_t smallW;

        image big_image;
    };

    struct DiffBody {
        using input_type = tuple<image, tuple<coords, image>>;
        using output_type = pair<coords, int>;

        output_type operator()(input_type const& input) {
            image const& first = get<0>(input);
            auto const& frame = get<1>(input);
            image const& second = get<1>(frame);
            coords const& origin = get<0>(frame);

            int difference = 0;
            int h = first.size();
            int w = first[0].size();
            for (int i = 0; i < h; ++i) {
                for (int j = 0; j < w; ++j) {
                    pixel const &p1 = first[i][j];
                    pixel const &p2 = second[i][j];
                    difference += abs((int)p1.r - (int)p2.r)
                                  + abs((int)p1.g - (int)p2.g)
                                  + abs((int)p1.b - (int)p2.b);
                }
            }

            return make_pair(origin, difference);
        }
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
    string big_image_path = "../../data/image.dat";


    // Graph activation
    MyFlowGraph g0(big_image_path, small_image_paths[0]);
    MyFlowGraph g1(big_image_path, small_image_paths[1]);
    MyFlowGraph g2(big_image_path, small_image_paths[2]);

    // Output
    g0.wait_for_all();
    g1.wait_for_all();
    g2.wait_for_all();

    g0.print_result(cout);
    g1.print_result(cout);
    g2.print_result(cout);

    return 0;
}