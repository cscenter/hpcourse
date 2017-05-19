#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>

#include <tbb/flow_graph.h>
#include <tbb/mutex.h>

using namespace std;
using namespace tbb::flow;
using namespace tbb;

string PATH_TO_DATA = "../../data/";

typedef tuple<int, int> coord;

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

void subimwrite(const image& source, const string&path, coord from, coord to, int delta=30) {
    // write subimage with some delta around
    int source_n_rows = source.size(), source_n_cols = source[0].size();
    int min_x = max(0, get<0>(from) - delta), min_y = max(0, get<1>(from) - delta),
            max_x = min(source_n_rows, get<0>(to) + delta), max_y = min(source_n_cols, get<1>(to) + delta);

    int h = max_x - min_x, w = max_y - min_y, d = 3;
    ofstream file(path, ios::binary);
    file.write(reinterpret_cast<char*>(&h), 4);
    file.write(reinterpret_cast<char*>(&w), 4);
    file.write(reinterpret_cast<char*>(&d), 4);

    for (int x = min_x; x < max_x; x++) {
        for (int y = min_y; y < max_y; y++) {
            pixel pix = source[x][y];
            file.write(reinterpret_cast<const char*>(&pix.r), 1);
            file.write(reinterpret_cast<const char*>(&pix.g), 1);
            file.write(reinterpret_cast<const char*>(&pix.b), 1);
        }
    }
    file.close();
}


int find_subimage(string source_path, string dest_path) {
    image big_image = imread(PATH_TO_DATA + "image.dat");
    int big_n_rows = big_image.size(), big_n_cols = big_image[0].size();

    // 1. Считываем маленькое изображение
    image small_image = imread(source_path);
    int small_n_rows = small_image.size(), small_n_cols = small_image[0].size();

    cout << "Big image size: " << big_n_rows << " x " << big_n_cols << endl
            << "Small image size: " << small_n_rows << " x " << small_n_cols << endl;

    graph g;

    // 2. Узел, генерирующий из большого изображения все возможные прямоугольники размера искомого изображения
    int cur_step = 0, steps_by_row = (big_n_cols - small_n_cols + 1),
            max_step = steps_by_row * (big_n_rows - small_n_rows + 1);
    source_node<coord> generate_subimages(g, [&]( coord &xy ) {
        if (cur_step < max_step - 1) {
            int step = ++cur_step;
            xy = coord(step / steps_by_row, step % steps_by_row);
            return true;
        } else {
            return false;
        }
    }, false);

    // 3. Буферный узел
    buffer_node<coord> subimages_buffer(g);

    // 4. Узел, подсчитывающий разницу между искомым изображением и кандидатом
    function_node<coord, tuple<int, coord>> diff_node(g, unlimited, [&](coord xy) {
        // calculate diff as sum of diffs by pixel by channel
        long diff = 0;
        int x = get<0>(xy), y = get<1>(xy);
        for (int dx = 0; dx < small_n_rows; dx++) {
            for (int dy = 0; dy < small_n_rows; dy++) {
                pixel p1 = big_image[x + dx][y + dy], p2 = small_image[dx][dy];
                diff += abs(p1.r - p2.r) + abs(p1.g - p2.g) + abs(p1.b - p2.b);
            }
        }

        return tuple<int, coord>(diff, xy);
    });

    // 5. Узел, содержащий результат - минимальную разницу и координаты верхнего левого угла.
    tuple<int, coord> min_diff_and_xy(numeric_limits<int>::max(), coord(0, 0));
    mutex min_mutex;
    function_node<tuple<int, coord>, int> min_reducer(g, unlimited, [&](tuple<int, coord> diff_and_xy) {
        bool need_to_exchange = get<0>(diff_and_xy) < get<0>(min_diff_and_xy);
        if (need_to_exchange) {
            // double check
            min_mutex.lock();
            need_to_exchange = get<0>(diff_and_xy) < get<0>(min_diff_and_xy);
            if (need_to_exchange) {
                // cout << get<0>(diff_and_xy) << "  " << get<0>(min_diff_and_xy) << endl;
                min_diff_and_xy = diff_and_xy;
            }
            min_mutex.unlock();
        }
        return 0;    // sorry
    });

    // Соединяем вершины ребрами
    make_edge(generate_subimages, subimages_buffer);
    make_edge(subimages_buffer, diff_node);
    make_edge(diff_node, min_reducer);


    // Запускаем
    generate_subimages.activate();

    g.wait_for_all();

    // 6. Записываем окрестность найденного изображения в файл.
    int min_x = get<0>(get<1>(min_diff_and_xy)), min_y = get<1>(get<1>(min_diff_and_xy));
    cout << "Found image with diff " << get<0>(min_diff_and_xy) << " and left upper corner at ("
         << min_x << ", " << min_y << ")" << endl;
    subimwrite(big_image, dest_path, pair<int, int>(min_x, min_y), pair<int, int>(min_x + small_n_rows, min_y + small_n_cols));
}


int main() {
    find_subimage(PATH_TO_DATA + "cheer.dat", PATH_TO_DATA + "cheer_found.dat");
    find_subimage(PATH_TO_DATA + "hat.dat", PATH_TO_DATA + "hat_found.dat");
    find_subimage(PATH_TO_DATA + "chicken.dat", PATH_TO_DATA + "chicken_found.dat");
}
