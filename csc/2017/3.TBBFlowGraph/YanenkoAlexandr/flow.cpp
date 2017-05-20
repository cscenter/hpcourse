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
    int h = (int)source.size();
    int w = (int)source[0].size();
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

struct ImageRange {
    std::size_t upper_x;
    std::size_t upper_y;
    std::size_t lower_x;
    std::size_t lower_y;
};

class ImageRangeGenerator {
private:
    queue<ImageRange> stored_image_ranges;
public:
    ImageRangeGenerator(const image &needle, const image &haystack) {
        for (std::size_t i = 0; i < haystack.size() - needle.size(); ++i) {
            for (std::size_t j = 0; j < haystack[0].size() - needle[0].size(); ++j) {
                stored_image_ranges.push({i, j, i + needle.size(), j + needle.size()});
            }
        }
    }

    bool operator()(ImageRange &out) {
        if (stored_image_ranges.size() == 0) {
            return false;
        }
        out = stored_image_ranges.front();
        stored_image_ranges.pop();
        return true;
    }
};

unsigned long pixel_difference(pixel x, pixel y) {
    return (unsigned long)(labs(x.r - y.r) + labs(x.g - y.g) + labs(x.b - y.b));
}

int main() {
    image haystack = imread("data/image.dat");

	std::array<string, 3> input_paths = {"data/cheer", "data/chicken", "data/hat"};

    for (string input_path : input_paths) {
        image needle = imread(input_path + ".dat");
        graph g;

        source_node<ImageRange> image_range_generator(g, ImageRangeGenerator(needle, haystack), false);

        buffer_node<ImageRange> image_range_buffer(g);

        function_node<ImageRange, pair<ImageRange, unsigned long>> difference_calculator(
                g, unlimited,
                [&needle, &haystack](ImageRange image_range) -> pair<ImageRange, unsigned long> {
                    unsigned long result = 0;
                    for (std::size_t i = 0; i < needle.size(); ++i) {
                        for (std::size_t j = 0; j < needle[0].size(); ++j) {
                            result += pixel_difference(
                                    needle[i][j],
                                    haystack[image_range.upper_x + i][image_range.upper_y + j]
                            );
                        }
                    }
                    return make_pair(image_range, result);
                }
        );

        buffer_node<pair<ImageRange, unsigned long>> difference_buffer(g);

        ImageRange best_image_range = {0, 0, needle.size(), needle[0].size()};
        unsigned long best_difference = std::numeric_limits<unsigned long>::max();
        function_node<pair<ImageRange, unsigned long>> min_difference_accumulator(
                g, 1,
                [&best_image_range, &best_difference](pair<ImageRange, unsigned long> pair) -> void {
                    ImageRange &image_range = pair.first;
                    unsigned long difference = pair.second;
                    if (difference < best_difference) {
                        best_difference = difference;
                        best_image_range = image_range;
                    }
                }
        );

        make_edge(image_range_generator, image_range_buffer);
        make_edge(image_range_buffer, difference_calculator);
        make_edge(difference_calculator, difference_buffer);
        make_edge(difference_buffer, min_difference_accumulator);

        image_range_generator.activate();
        g.wait_for_all();

        cout << best_image_range.upper_x << " " << best_image_range.upper_y << "\n";

        image result = vector<vector<pixel>>(best_image_range.lower_x - best_image_range.upper_x);
        for (auto &row : result) {
            row.resize(best_image_range.lower_y - best_image_range.upper_y);
        }

        for (std::size_t i = best_image_range.upper_x; i < best_image_range.lower_x; ++i) {
            for (std::size_t j = best_image_range.upper_y; j < best_image_range.lower_y; ++j) {
                result[i - best_image_range.upper_x][j - best_image_range.upper_y] = haystack[i][j];
            }
        }

        string result_path = input_path + "_result.dat";
        imwrite(result, result_path);
    };

    return 0;
}
