#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <limits>

#include <tbb/flow_graph.h>
#include <cassert>
#include <memory>

using namespace tbb::flow;
using namespace tbb;


struct pixel {
    uint8_t r = 0;
    uint8_t g = 0;
    uint8_t b = 0;

    friend std::ostream & operator<<(std::ostream & out, pixel const & p) {
        std::cout << "r: " << (int)p.r << "; g: " << (int)p.g << "; b: " << (int)p.b << std::endl;
    }

    uint8_t operator-(pixel const & p) {
        return std::abs(r - p.r) + std::abs(g - p.g) + std::abs(b - p.b);
    }
};

using image = std::vector<std::vector<pixel>>;

struct ImageRect {
    std::uint32_t x = 0;
    std::uint32_t y = 0;
    std::uint32_t height = 0;
    std::uint32_t width = 0;

    ImageRect() {}

    ImageRect(std::uint32_t const h, std::uint32_t const w, std::uint32_t const d)
            : height(h), width(w), x(0), y(0)
    {}

    ImageRect(std::uint32_t const _x, std::uint32_t const _y, std::uint32_t const h, std::uint32_t const w)
            : x(_x), y(_y), height(h), width(w)
    {}

    ImageRect(ImageRect const & m) : height(m.height), width(m.width), x(m.x), y(m.y)
    {}

    ImageRect(image const & m) : height(m.size()), width(m[0].size()), x(0), y(0) {}

    ImageRect & operator=(ImageRect const & m) {
        if(this != &m) {
            height = m.height;
            width = m.width;
            x = m.x;
            y = m.y;
        }
        return *this;
    }
};


// read and write
image imread(const std::string& path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        std::cerr << "Can read only prepared .dat files!" << std::endl;
        throw std::invalid_argument(path);
    }

    std::ifstream file(path, std::ios::binary | std::ios::in);

    std::uint32_t h = 0, w = 0, d = 0;
    assert(file.is_open());

    file.read(reinterpret_cast<char *>(&h), 4);
    file.read(reinterpret_cast<char *>(&w), 4);
    file.read(reinterpret_cast<char *>(&d), 4);

    auto data = image(h);
    for (auto& row: data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = std::array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = pixel { uint8_t(pix[0]),
                                 uint8_t(pix[1]),
                                 uint8_t(pix[2])};
        }
    }

    return data;
}

void imwrite(const image & source, const std::string& path) {
    int h = source.size();
    int w = source[0].size();
    int d = 3;
    std::ofstream file(path, std::ios::binary);
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


// create template image and fill it with data from bigImage
void create_image(image & data, ulong const height, ulong const width) {
    data.resize(height);
    for (auto& row: data) {
        row.resize(width);
    }
}

void fill_image(ImageRect const & candidate, image & data, image const & bigImage) {
    int h = candidate.height;
    int w = candidate.width;
    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            data[i][j] = bigImage[candidate.x + i][candidate.y + j];
        }
    }
}


//// Алгоритм Евклида для вычисления наибольшего общего делителя
//ulong NOD(ulong x, ulong y) {
//    while (x != y)
//        if (x > y)
//            x -= y;
//        else
//            y -= x;
//    return x;
//}
////
//void dist(image img, std::vector<double> &mft, int cell_size, int power) {
//    // Массив интенсивностей, каждый элемент которого будет содержать суммарную интенсивность пикселей внутри некоторой ячейки.
//    std::vector<double> cells_intencities(mft.size());
//    // Индекс ячейки в массиве интенсивностей, для которой в данный момент считается интенсивность
//    int idx = 0;
//    // Общая интенсивность.
//    double sum_intensity = 0;
//    for (size_t i = 0; i < img.size(); i += cell_size)
//        for (size_t j = 0; j < img[0].size(); j += cell_size) {
//            // Считаем сумму интенсивностей внутри ячейки размера cell_size * cell_size
//            for (size_t k = i; k < i + cell_size; ++k)
//                for (size_t l = j; l < j + cell_size; ++l) {
//                    // Считаем сумму интенсивностей для ячейки с индексом idx
//                    cells_intencities[idx] += (double)img[k][l];
//                    // Считаем интенсивность для всего изображения
//                    sum_intensity += (double)img[k][l];
//                }
//            // Переходим к следующей ячейке
//            ++idx;
//        }
//    // Вектор, каждый элемент которого хранит отношение суммы интенсивностей пикселей данной ячейки к сумме интенсивностей пикселей всего изображения
//    std::vector<double> probability(cells_intencities.size());
//    // Сумма отношений интенсивностей
//    double sum_of_probability = 0;
//    // Считаем отношения интенсивностей и сумму этих отношений
//    for (size_t i = 0; i < cells_intencities.size(); ++i) {
//        probability[i] = pow(cells_intencities[i] / sum_intensity, power);
//        sum_of_probability += probability[i];
//    }
//    // Считаем значения мультифрактального преобразования
//    for (size_t i = 0; i < mft.size(); ++i) {
//        mft[i] = probability[i] / sum_of_probability;
//    }
//}

int calc_diff(ImageRect const & candidate, image & smallImage, image const & big_image) {
    int difference = 0;
    int x = candidate.x;
    int y = candidate.y;
    int h = candidate.height;
    int w = candidate.width;
    for (size_t i = 0; i < h; ++i) {
        for (size_t j = 0; j < w; ++j) {
            difference += smallImage[i][j] - big_image[x + i][y + j];
        }
    }
    return difference;
}


int main() {
    graph g;

    function_node< std::string, tuple<image, ImageRect> > load_small_image(
            g, 1,
            [](std::string const & path) -> tuple<image, ImageRect>
    {
        image img = std::move(imread(path));
        ImageRect imageRect(img);
        return make_tuple(img, imageRect);
    } );

    std::string pathToBigImage = "/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/image.dat";
    image bigImage = std::move(imread(pathToBigImage));

    function_node< tuple<uint32_t, uint32_t, image>, tuple<int, ImageRect> > calcualte_difference(
            g, tbb::flow::unlimited,
            [&](tuple<uint32_t, uint32_t, image> const & args) -> tuple<int, ImageRect>
    {
        auto x_top_left_corner = get<0>(args);
        auto y_top_left_corner = get<1>(args);
        image smallImage = get<2>(args);

        auto template_height = smallImage.size();
        auto template_width = smallImage[0].size();
        ImageRect candidate(x_top_left_corner, y_top_left_corner, template_height, template_width);

        int result = calc_diff(candidate, smallImage, bigImage);
        return std::make_tuple(result, candidate);
    } );


    function_node< tuple<image, ImageRect>, tuple<uint32_t, uint32_t, image, image> > create_rect_and_calc_diff(
            g, tbb::flow::unlimited,
            [&](tuple<image, ImageRect> const & args ) -> tuple<uint32_t, uint32_t, image, image>
    {
        ImageRect imageRect = get<1>(args);
        auto h = imageRect.height;
        auto w = imageRect.width;
        auto rows = bigImage.size();
        auto cols = bigImage[0].size();

        for (auto i = 0; i + h < rows; ++i) {
            for (auto j = 0; j + w < cols; ++j) {
                calcualte_difference.try_put(make_tuple(i, j, get<0>(args)));
            }
        }
    }
    );

    buffer_node< tuple<int, ImageRect> > min_buffer_node(g);

    int result = std::numeric_limits<int>::max();

    function_node<tuple<int, ImageRect>, int > find_min(
            g, tbb::flow::unlimited,
            [&](tuple<int, ImageRect> const & args) ->  int
    {
        int temp_diff = get<0>(args);
        ImageRect same_image = get<1>(args);
        result = std::min(result, temp_diff);
    }
    );

    make_edge(load_small_image, create_rect_and_calc_diff);
    make_edge(calcualte_difference, min_buffer_node);
    make_edge(min_buffer_node, find_min);

    load_small_image.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/cheer.dat");
//    load_small_image.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/chicken.dat");
//    load_small_image.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/hat.dat");
    g.wait_for_all();
    std::cout << result;

    return 0;
}


//    start_rectangles_search.try_put(ImageRect(0, 0, 0));
//    start_rectangles_search.try_put(ImageRect(1, 0, 0));
//    start_rectangles_search.try_put(ImageRect(2, 0, 0));
//    make_edge(start_rectangles_search, calc_diff_between_small_image_and_pattern);

//    function_node< tuple<image, ImageRect>, tuple<image, image, vector<ImageRect>> > findRectOnBigImage(
//            g, tbb::flow::unlimited,
//            [](tuple<image, ImageRect> const & args) -> tuple<image, image, vector<ImageRect>>
//    {
//        image smallImage = get<0>(args);
//        ImageRect smallImageMat = get<1>(args);
//        string pathToBigImage = "/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/image.dat";
//        image bigImage = std::move(imread(pathToBigImage));
//        auto h = smallImageMat.height;
//        auto w = smallImageMat.width;
//        ulong rows = bigImage.size();
//        ulong cols = bigImage[0].size();
//        vector<ImageRect> image_candidates;
//        image_candidates.reserve(rows * cols);
//        ImageRect pattern(h, w, smallImageMat.depth);
//        int count = 0;
//        for (auto i = 0; i + h < rows; ++i) {
//            for (auto j = 0; j + w < cols; ++j) {
//                update_pattern(i, j, pattern);
//                image_candidates.push_back(pattern);
//                ++count;
//            }
//            if (count >= 100000)
//                break;
//        }
//        return make_tuple(smallImage, bigImage, image_candidates);
//    } );
//
//    function_node< tuple<image, image, vector<ImageRect> >, double> calcDiffBetweenPatternAndGenuineImage(
//            g, tbb::flow::unlimited,
//            [](tuple<image, image, vector<ImageRect>> const & args) -> double
//    {
//        image smallImage = get<0>(args);
//        image bigImage = get<1>(args);
//        vector<ImageRect> candidate_matrices = get<2>(args);
//        image template_image;
//        ulong template_height = smallImage.size();
//        ulong template_width = smallImage[0].size();
//        create_image(template_image, template_height, template_width);
//        double result = std::numeric_limits<double >::max();
//        for (auto q = 0; q < candidate_matrices.size(); ++q) {
//            fill_image(candidate_matrices[q], template_image, bigImage);
//             Считаем мультифрактальное преобразование порядка i для каждого изображения
//            result = min(result, calc_diff(template_height, template_width, smallImage, template_image));
//        }
//        cout << result;
//        return result;
//    });

//    std::vector<std::tuple<std::string, unsigned int, std::string>> commands;
//    auto n = commands.size();
//    tbb::parallel_for(tbb::blocked_range<std::size_t>(0, n),
//                      [&](const tbb::blocked_range<std::size_t> &range) {
//                          for (auto i = range.begin(); i != range.end(); ++i)
//                              const auto &tuple = commands[i];
//                      } );

//    createMatFromImage.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/chicken.dat");
//    make_edge(createMatFromImage, findRectOnBigImage);
//    make_edge(findRectOnBigImage, calcDiffBetweenPatternAndGenuineImage);