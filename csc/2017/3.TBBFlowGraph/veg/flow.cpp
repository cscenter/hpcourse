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


int main() {
    image big_image = imread("/home/user/Documents/study/parallel/hpcourse/csc/2017/3.TBBFlowGraph/data/image.dat");
    int big_n_rows = big_image.size(), big_n_cols = big_image[0].size();

    graph g;

    // 1. Узел, принимающий путь к файлу с маленьким изображением и передающий матрицу изображения
    function_node<string, image> read_image(g, 1, [](string path) {
        cout << "Processing " << path << endl;
        image im = imread(path);
        cout << "Image size: " << im.size() << " x " << im[0].size() << endl;
        return im;
    });

    // 2. Узел, принимающий матрицу и генерирующий из большого изображения все возможные прямоугольники размера искомого изображения
    function_node<image, image> generate_subimages(g, unlimited, [&](image small_image, int step=0) {
        int n_rows = small_image.size(), n_cols = small_image[0].size();
        int row_step = big_n_cols - n_cols + 1;
        int cur_row = step / row_step, cur_col = step % row_step;

        cout << "Got step " << step << ", cur row = " << cur_row << ", cur col = " << cur_col << endl;
        return small_image;  // TODO
    });

    // 3. Буферный узел
    buffer_node<image> subimages_buffer(g);


    // 4. Узел, подсчитывающий разницу между искомым изображением и кандидатом


    // 5. Узел, содержащий результат - минимальную разницу и координаты верхнего левого угла.


    // 6. Узел, записывающий окрестность найденного изображения в файл.


    // Соединяем вершины ребрами
    make_edge(read_image, generate_subimages);
    make_edge(generate_subimages, subimages_buffer);


    // Запускаем
    string default_path = "/home/user/Documents/study/parallel/hpcourse/csc/2017/3.TBBFlowGraph/data/cheer.dat";
    read_image.try_put(default_path);

    g.wait_for_all();
}
