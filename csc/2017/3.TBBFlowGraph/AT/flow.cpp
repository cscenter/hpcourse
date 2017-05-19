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

struct sub_image {
	uint32_t x, y;
	sub_image() {}
	sub_image(uint32_t x, uint32_t y) :x(x), y(y) {}
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
	for (auto& row : data) {
		row.resize(w);
	}

	for (uint32_t i = 0; i < h; ++i) {
		for (uint32_t j = 0; j < w; ++j) {
			auto pix = array<char, 3>();
			file.read(pix.data(), 3);
			data[i][j] = pixel{ uint8_t(pix[0]),
				uint8_t(pix[1]),
				uint8_t(pix[2]) };
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
		for (auto& pix : row) {
			file.write(reinterpret_cast<const char*>(&pix.r), 1);
			file.write(reinterpret_cast<const char*>(&pix.g), 1);
			file.write(reinterpret_cast<const char*>(&pix.b), 1);
		}
	}
	file.close();
}

class CreateSubImages {
	queue<sub_image> output;
public:
	CreateSubImages(image &big, image &small) {
		for (uint32_t i = 0; i < big.size() - small.size(); i++) {
			for (uint32_t j = 0; j < big[0].size() - small[0].size(); j++) {
				output.push(sub_image(i, j));
			}
		}
	}

	bool operator()(sub_image &v) {
		if (output.empty())
			return false;

		v = output.front();
		output.pop();
		return true;
	}
};

struct ComputeDiff {
	image big, small;

	ComputeDiff() {}
	ComputeDiff(image big, image small) : big(big), small(small) {}

	pair<sub_image, int64_t> operator()(const sub_image &subImage) {
		uint64_t diff = 0;
		for (uint32_t i = 0; i < small.size(); i++) {
			for (uint32_t j = 0; j < small[0].size(); j++) {
				pixel bp = big[subImage.x + i][subImage.y + j];
				pixel sp = small[i][j];
				diff += (abs(bp.r - sp.r) + abs(bp.g - sp.g) + abs(bp.b - sp.b));
			}
		}
		return make_pair(subImage, diff);
	}
};

int main() {
	image big = imread("data/image.dat");
	pair<string, string> paths[] = {
		make_pair("data/cheer.dat", "data/cheer_result.dat"),
		make_pair("data/chicken.dat", "data/chicken_result.dat"),
		make_pair("data/hat.dat", "data/hat_result.dat")
	};
	std::cout << "Results (upper-left corner + output file name)" << endl;
	
	for (pair<string, string> path : paths) {
		image small = imread(path.first);

		graph g;
		source_node<sub_image> input(g, CreateSubImages(big, small), false);
		buffer_node<sub_image> buffer(g);
		function_node<sub_image, pair<sub_image, uint64_t>> diff(g, unlimited, ComputeDiff(big, small));

		uint64_t minDiff = 1000000000;
		sub_image finalSubImage;
		function_node<pair<sub_image, uint64_t>> result(g, 1, [&](pair<sub_image, uint64_t> v) {
			if (v.second < minDiff) {
				minDiff = v.second;
				finalSubImage = v.first;
			}
		});

		make_edge(input, buffer);
		make_edge(buffer, diff);
		make_edge(diff, result);

		input.activate();
		g.wait_for_all();

		std::cout << path.first << ": [" << finalSubImage.x << "; " << finalSubImage.y << "], [" << path.second << "]" << endl;

		{	//save result image
			image result(small.size(), vector<pixel>(small[0].size()));
			for (uint32_t i = 0; i < small.size(); i++) {
				for (uint32_t j = 0; j < small[0].size(); j++) {
					result[i][j] = big[finalSubImage.x + i][finalSubImage.y + j];
				}
			}
			imwrite(result, path.second);
		}
	}
	//system("pause");
}