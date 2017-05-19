#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>

#include <tbb/flow_graph.h>

using namespace tbb::flow;

struct pixel {
    std::uint8_t r;
    std::uint8_t g;
    std::uint8_t b;
};

using image = std::vector<std::vector<pixel>>;

image imread(const std::string& path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        std::cerr << "Can read only prepared .dat files!" << std::endl;
        throw std::invalid_argument(path);
    }
    std::cout << "reading " << path << "\n";
    std::ifstream file(path, std::ios::binary | std::ios::in);

    if(file.is_open()) {
        std::uint32_t h, w, d;
        file.read(reinterpret_cast<char *>(&h), 4);
        file.read(reinterpret_cast<char *>(&w), 4);
        file.read(reinterpret_cast<char *>(&d), 4);

        auto data = std::vector<std::vector<pixel>>(h);
        for (auto &row: data) {
            row.resize(w);
        }

        for (std::uint32_t i = 0; i < h; ++i) {
            for (std::uint32_t j = 0; j < w; ++j) {
                auto pix = std::array<char, 3>();
                file.read(pix.data(), 3);
                data[i][j] = pixel {uint8_t(pix[0]),
                                    uint8_t(pix[1]),
                                    uint8_t(pix[2])};
            }
        }

        return data;
    }
    else {
        throw std::invalid_argument(path);
    }
}

void imwrite(const image& source, const std::string& path) {
    int h = source.size();
    int w = source[0].size();
    int d = 3;

    std::cout << "writing " << path << "\n\n";
    std::ofstream file(path, std::ios::binary);

    if(file.is_open()) {
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
    else {
        throw std::invalid_argument(path);
    }
}


struct ImageRectangle {
    size_t TopLeftY_ = 0;
    size_t TopLeftX_ = 0;
    size_t BottomRightY_ = 0;
    size_t BottomRightX_ = 0;

    ImageRectangle() = default;
    ~ImageRectangle() = default;

    ImageRectangle(size_t top_left_y,
                   size_t top_left_x,
                   size_t bottom_right_y,
                   size_t bottom_right_x) : TopLeftY_(top_left_y),
                                            TopLeftX_(top_left_x),
                                            BottomRightY_(bottom_right_y),
                                            BottomRightX_(bottom_right_x)
    {}
};

class RectangleGenerator {
    std::vector<ImageRectangle> ImageRects_;
    
public:
    RectangleGenerator() = default;
    ~RectangleGenerator() = default;

    RectangleGenerator(image const & target_image, image const & example_image) {
        size_t const y_last = example_image.size() - target_image.size();
        size_t const x_last = example_image.back().size() - target_image.back().size();
        size_t const target_height = target_image.size();
        size_t const target_width = target_image.back().size();
        
        ImageRects_.reserve(y_last * x_last);
        
        for (size_t y = 0; y != y_last; ++y) {
            for (size_t x = 0; x != x_last; ++x) {
                ImageRects_.push_back({y, x, y + target_height - 1, x + target_width - 1});
            }
        }
    }

    bool operator()(ImageRectangle &result) {
        if (ImageRects_.empty())
            return false;

        ImageRectangle yet_another_image_rect = ImageRects_.back();
        ImageRects_.pop_back();

        result = yet_another_image_rect;
        return true;
    }
};

class DifferenceProcessor {
    image const & ExampleImageRef_;
    image const & TargetImageRef_;
    size_t TargetHeight_;
    size_t TargetWidth_;

    DifferenceProcessor() = delete;

public:
    ~DifferenceProcessor() = default;
    DifferenceProcessor(image const & example_image,
                        image const & target_image) : ExampleImageRef_(example_image),
                                                      TargetImageRef_(target_image),
                                                      TargetHeight_(target_image.size()),
                                                      TargetWidth_(target_image.back().size())
    {}

    int32_t processDifference(pixel const & lhs, pixel const & rhs) const {
        return abs(static_cast<int32_t>(lhs.r) - static_cast<int32_t>(rhs.r)) +
               abs(static_cast<int32_t>(lhs.g) - static_cast<int32_t>(rhs.g)) +
               abs(static_cast<int32_t>(lhs.b) - static_cast<int32_t>(rhs.b));
    }

    std::tuple<ImageRectangle, int64_t> operator()(const ImageRectangle & checkable_image_rect) {
        int64_t difference {};

        for (size_t y = 0; y != TargetHeight_; ++y) {
            for (size_t x = 0; x != TargetWidth_; ++x) {
                difference += processDifference(ExampleImageRef_[checkable_image_rect.TopLeftY_ + y][checkable_image_rect.TopLeftX_ + x],TargetImageRef_[y][x]);
            }
        }

        return std::make_tuple(checkable_image_rect, difference);
    }

};


class OccurenceUpdater {
    int64_t & MinimalDifferenceRef_;
    ImageRectangle & ClosestImageOccurenceRef_;

public:
    OccurenceUpdater(int64_t & current_minimal_difference,
                     ImageRectangle & closest_image_occurence) : MinimalDifferenceRef_(current_minimal_difference),
                                                                 ClosestImageOccurenceRef_(closest_image_occurence)
    {}

    void operator()(std::tuple<ImageRectangle,int64_t> checkable_image_diff) {
        ImageRectangle & checkable_image_occurence = std::get<0>(checkable_image_diff);
        int64_t checkable_min_difference =           std::get<1>(checkable_image_diff);

        if (checkable_min_difference < MinimalDifferenceRef_) {
            MinimalDifferenceRef_ = checkable_min_difference;
            ClosestImageOccurenceRef_ = checkable_image_occurence;
        }
    }
};


void checkAndWriteOccurence(const std::string &target_image_path, const std::string &occur_image_path, image const &example_image) {
    graph occurence_calculator_graph;

    image target_image = imread(target_image_path);

    source_node<ImageRectangle> generator_node(occurence_calculator_graph,
                                               RectangleGenerator(target_image, example_image),
                                               true);

    buffer_node<ImageRectangle> generator_buffer(occurence_calculator_graph);

    function_node<ImageRectangle, std::tuple<ImageRectangle,int64_t>> difference_processor_node(occurence_calculator_graph,
                                                                                                unlimited,
                                                                                                DifferenceProcessor(example_image, target_image));

    buffer_node<std::tuple<ImageRectangle,int64_t>> difference_buffer(occurence_calculator_graph);

    int64_t cur_minimal_difference = std::numeric_limits<int64_t>::max();
    ImageRectangle closest_image_occurence;

    function_node<std::tuple<ImageRectangle,int64_t>> closet_occurence_updater(occurence_calculator_graph,
                                                                               unlimited,
                                                                               OccurenceUpdater(cur_minimal_difference, closest_image_occurence));

    make_edge(generator_node, generator_buffer);
    make_edge(generator_buffer, difference_processor_node);
    make_edge(difference_processor_node, difference_buffer);
    make_edge(difference_buffer, closet_occurence_updater);

    occurence_calculator_graph.wait_for_all();

    std::cout << "top_left_x     : " << closest_image_occurence.TopLeftX_
              << "\ntop_left_y     : " << closest_image_occurence.TopLeftY_
              << "\nbottom_right_x : " << closest_image_occurence.BottomRightX_
              << "\nbottom_right_y : " << closest_image_occurence.BottomRightY_ << "\n";

    size_t target_height = closest_image_occurence.BottomRightY_ - closest_image_occurence.TopLeftY_ + 1;
    size_t target_width = closest_image_occurence.BottomRightX_ - closest_image_occurence.TopLeftX_ + 1;

    image occurence_image(target_height, std::vector<pixel>(target_width));

    for (size_t y = 0; y != target_height; ++y) {
        for (size_t x = 0; x != target_width; ++x) {
            occurence_image[y][x] = example_image[y + closest_image_occurence.TopLeftY_][x + closest_image_occurence.TopLeftX_];
        }
    }

    imwrite(occurence_image, occur_image_path);
}

std::string const PATH_IMAGE   = "../../data/image.dat";

std::string const PATH_HAT     = "../../data/hat.dat";
std::string const PATH_OCCURENCE_HAT     = "../../data/occurence_hat.dat";

std::string const PATH_CHICKEN = "../../data/chicken.dat";
std::string const PATH_OCCURENCE_CHICKEN = "../../data/occurence_chicken.dat";

std::string const PATH_CHEER   = "../../data/cheer.dat";
std::string const PATH_OCCURENCE_CHEER   = "../../data/occurence_cheer.dat";

int main() {

    image example_image = imread(PATH_IMAGE);

    checkAndWriteOccurence(PATH_HAT, PATH_OCCURENCE_HAT, example_image);
    checkAndWriteOccurence(PATH_CHICKEN, PATH_OCCURENCE_CHICKEN, example_image);
    checkAndWriteOccurence(PATH_CHEER, PATH_OCCURENCE_CHEER, example_image);

    return 0;
}

