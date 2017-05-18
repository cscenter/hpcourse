#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

#include <atomic>
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

void imwrite(const image & source, const std::string & path) {
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

pixel ** create_ptr(image const & img) {
    std::uint32_t height = img.size();
    std::uint32_t width = img[0].size();
    pixel ** img_buff = new pixel * [height];
    img_buff[0] = new pixel[height * width];
    for (auto i = 1; i < height; ++i) {
        img_buff[i] = img_buff[i - 1] + width;
    }
    for (auto i = 0; i < height; ++i) {
        for (auto j = 0; j < width; ++j) {
            img_buff[i][j] = img[i][j];
        }
    }
    return img_buff;
}

using image_ptr = pixel **;

void free_buffer(image_ptr & ptr) {
    delete [] ptr[0];
    delete [] ptr;
}

int calc_diff(ImageRect const & candidate, image const & big_image, image_ptr & smallImage) {
    int difference = 0;
    int x = candidate.x;
    int y = candidate.y;
    int h = candidate.height;
    int w = candidate.width;
    for (auto i = 0; i < h; ++i) {
        for (auto j = 0; j < w; ++j) {
            difference += smallImage[i][j] - big_image[x + i][y + j];
        }
    }
    return difference;
}

using diff_Rect_ImagePtr
= tuple<int, ImageRect, image_ptr>;

using node_input_String_output_ImagePtr_ImageRect
    = function_node <
        std::string,
        tuple<image_ptr, ImageRect>
    >;

using node_input_X_Y_H_W_ImagePtr_output_Diff_Image_Rect
    = function_node <
        tuple<uint32_t, uint32_t, uint32_t, uint32_t, image_ptr>,
        diff_Rect_ImagePtr
    >;

using node_input_ImagePtr_ImageRect_output_X_Y_H_W_ImagePtr
    = function_node <
        tuple<image_ptr, ImageRect>,
        tuple<uint32_t, uint32_t, uint32_t, uint32_t, image_ptr>
    >;


using node_input_Diff_ImageRect_output_int
    = function_node <
        diff_Rect_ImagePtr,
        int
    >;

using X_Y_H_W_Image_Ptr
    = tuple<uint32_t, uint32_t, uint32_t, uint32_t, image_ptr>;

void reset_atomic_and_ptr(std::atomic<int> &value, image_ptr &ptr) {
    value = INT32_MAX;
    free_buffer(ptr);
}

void save_image_from_big_image(ImageRect const & imageRect, image const & bigImage, std::string const & path) {
    auto x = imageRect.x;
    auto y = imageRect.y;
    auto h = imageRect.height;
    auto w = imageRect.width;
    image result_image;
    result_image.resize(3 * h);
    for (auto i = 0; i < 3 * h; ++i) {
        result_image[i].resize(3 * w);
    }

    // change top left corner, if rectangle is not close to bigImage border
    if ((x - h > 0) && (y - w > 0)) {
        x -= h;
        y -= w;
    }

    for (auto i = 0; i < 3 * h; ++i) {
        result_image[i].resize(3 * w);
        for (auto j = 0; j < 3 * w; ++j) {
            result_image[i][j] = bigImage[x + i][y + j];
        }
    }
    imwrite(result_image, path);
}
int main() {

    std::string pathToBigImage = "/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/image.dat";
    image bigImage = std::move(imread(pathToBigImage));

    graph g;

    node_input_String_output_ImagePtr_ImageRect load_small_image(
            g, 1,
    [](std::string const & path) -> tuple<image_ptr, ImageRect>
    {
        image img = std::move(imread(path));
        ImageRect imageRect(img);
        image_ptr ptr = create_ptr(img);
        return std::make_tuple(ptr, imageRect);
    } );


    node_input_X_Y_H_W_ImagePtr_output_Diff_Image_Rect calculate_difference(
            g, tbb::flow::unlimited,
    [&](X_Y_H_W_Image_Ptr const & args) -> diff_Rect_ImagePtr
    {
        auto x_top_left_corner = get<0>(args);
        auto y_top_left_corner = get<1>(args);

        auto template_height = get<2>(args);
        auto template_width = get<3>(args);

        auto ptr = get<4>(args);
        ImageRect rectangle(x_top_left_corner, y_top_left_corner, template_height, template_width);

        int diff = calc_diff(rectangle, bigImage, ptr);
        return std::make_tuple(diff, rectangle, ptr);
    } );


    node_input_ImagePtr_ImageRect_output_X_Y_H_W_ImagePtr create_rect_and_calc_diff(
            g, tbb::flow::unlimited,
    [&](tuple<image_ptr, ImageRect> const & args ) -> X_Y_H_W_Image_Ptr
    {
        auto ptr = get<0>(args);
        ImageRect rectangle = get<1>(args);
        auto h = rectangle.height;
        auto w = rectangle.width;
        auto rows = bigImage.size();
        auto cols = bigImage[0].size();

        for (auto i = 0; i + h < rows; ++i) {
            for (auto j = 0; j + w < cols; ++j) {
                calculate_difference.try_put(std::make_tuple(i, j, h, w, ptr));
            }
        }
    }
    );

    std::atomic<int> result_diff(INT32_MAX);
    ImageRect result_rect;
    image_ptr ptr = nullptr;

    node_input_Diff_ImageRect_output_int find_min(
            g, tbb::flow::unlimited,
    [&](diff_Rect_ImagePtr const & args) -> int
    {
        while(1) {
            int temp_diff = get<0>(args);
            int current_result_diff = result_diff;
            if (temp_diff < current_result_diff) {
                if (result_diff.compare_exchange_strong(current_result_diff, temp_diff)) {
                    std::cout << result_diff << std::endl;
                    result_rect = get<1>(args);
                    ptr = get<2>(args);
                    break;
                };
            } else {
                break;
            }
        }
    }
    );

    make_edge(load_small_image, create_rect_and_calc_diff);
    make_edge(calculate_difference, find_min);

    load_small_image.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/cheer.dat");
    g.wait_for_all();
    save_image_from_big_image(result_rect, bigImage,"/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/my_cheer.dat");
    reset_atomic_and_ptr(result_diff, ptr);

    load_small_image.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/chicken.dat");
    g.wait_for_all();
    save_image_from_big_image(result_rect, bigImage,"/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/my_chicken.dat");
    reset_atomic_and_ptr(result_diff, ptr);

    load_small_image.try_put("/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/hat.dat");
    g.wait_for_all();
    save_image_from_big_image(result_rect, bigImage,"/home/montura/yandexDisk/Projects/Clion/TBBFlowGraph/data/my_hat.dat");
    reset_atomic_and_ptr(result_diff, ptr);

    return 0;
}
