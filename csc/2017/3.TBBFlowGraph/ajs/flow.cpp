#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <mutex>
#include <climits>

#include <tbb/flow_graph.h>
#include <tbb/concurrent_unordered_map.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

int operator-(const pixel& a, const pixel& b) {
    return abs(a.r - b.r)
        + abs(a.g - b.g)
        + abs(a.b - b.b);
}

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

struct images {
    const image source;
    const image child;
    const string child_path;

    images(const image& source, image child, string child_path) :
        source(source), child(child), child_path(child_path) {};
    images() {};
};

struct offset {
    int dx;
    int dy;

    offset(int dx, int dy) : dx(dx), dy(dy) {};
    offset() {};
};

struct image_task {
    images* i;
    offset o;

    image_task(images* i, offset o): o(o), i(i) {};
    image_task() {};
};

// Reads image from path
struct ImageReader {
    const image& source_image;

    ImageReader(const image& source_image) : source_image(source_image) {}

    images* operator()(const string& path) {
        const image child_image = imread(path);
        return new images(source_image, child_image, path);
    }
};

typedef multifunction_node<images*, tuple<image_task*>> source_slicer_node;
struct SourceSlicer {
    void operator()(images* d, source_slicer_node::output_ports_type &op) {
        auto source_image = d->source;
        auto child_image = d->child;
        int so_h = source_image.size();
        int so_w = source_image[0].size();
        int ch_h = child_image.size();
        int ch_w = child_image[0].size();

        int cnt = 0;
        for(int dx = 0; dx < so_h - ch_h; ++dx) {
            for(int dy = 0; dy < so_w - ch_w; ++dy) {
                auto o = offset(dx, dy);
                auto t = new image_task(d, o);
                cnt++;
                get<0>(op).try_put(t);
            }
        } 
    }
};

struct Calculator {
    tuple<int, image_task*> operator()(image_task* t) {
        const image& source_image = t->i->source;
        const image& child_image = t->i->child;

        const int& dx = t->o.dx;
        const int& dy = t->o.dy;

        int acc = 0;
        for(int i = 0; i < child_image.size(); ++i) {
            for(int j = 0; j < child_image[0].size(); ++j) {
                acc += source_image[i+dx][j+dy] - child_image[i][j];

            }
        }
        return make_tuple(acc, t);
    }
};

struct MinReducer{
    tbb::concurrent_unordered_map<string, tuple<int, image_task*>>* mins;

    MinReducer(
        tbb::concurrent_unordered_map<string, tuple<int, image_task*>>* m
    ) : mins(m) {};

    tuple<int, image_task*> operator()(tuple<int, image_task*> d) {
        image_task* t = get<1>(d);
        const string& key = t->i->child_path;

        int score = get<0>(d);

        int cur_min = get<0>(mins->at(key)); 

        if (score < cur_min) {
            mins->at(key) = d;
        }
        return d;
    }
};

void process_image(image source_image, vector<vector<string>> data) {
    graph g;
    tbb::concurrent_unordered_map<string, tuple<int, image_task*>> mins;

    // Node to read image from file
    function_node<string, images*> image_reader(
        g, unlimited, ImageReader(source_image)
    );

    // Node to make subimages from big image
    source_slicer_node source_slicer(g, serial, SourceSlicer());

    // Buffer node to handle each subrectangle of main image
    buffer_node<image_task*> big_buffer(g);

    // Node to calculate difference between image and region
    function_node<image_task*, tuple<int, image_task*>> calculator(
        g, unlimited, Calculator()
    );

    // Buffer to store diff results
    buffer_node<tuple<int, image_task*>> diff_buffer(g);

    // Reducer
    function_node<tuple<int, image_task*>, tuple<int, image_task*>> min_reducer(
        g, serial, MinReducer(&mins));

    make_edge(image_reader, source_slicer);
    make_edge(output_port<0>(source_slicer), big_buffer);
    make_edge(big_buffer, calculator);
    make_edge(calculator, diff_buffer);
    make_edge(diff_buffer, min_reducer);

    for (auto& data_pair : data) {
        image_reader.try_put(data_pair[0]);
        image_task* i;
        mins[data_pair[0]] = make_tuple(INT_MAX, i);
    }
    g.wait_for_all();

    for (auto& data_pair : data) {
        string key = data_pair[0];
        string output_path = data_pair[1];

        int min = get<0>(mins[key]);
        auto child = get<1>(mins[key])->i->child;
        auto o = get<1>(mins[key])->o;
        int dx = o.dx;
        int dy = o.dy;

        cout << "Found \"" << key;
        cout << " in: [(" << dx << "," << dy << ") ; (";
        cout << dx + child.size() << "," << dy + child[0].size() << ")]";
        cout << " with score: " << min;
        cout << endl;
    }
}

int main(int argc, char** argv) {
    image source_image = imread("../data/image.dat");

    vector<vector<string>> data = {
        {"../data/chicken.dat", "./chicken.out"},
        {"../data/hat.dat", "./hat.out"},
        {"../data/cheer.dat", "./cheer.out"}
    };
    
    process_image(source_image, data);
    
    return 0;
}
