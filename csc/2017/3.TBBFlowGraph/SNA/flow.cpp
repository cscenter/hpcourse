#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>

#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
	uint8_t r;
	uint8_t g;
	uint8_t b;
};


const string bigImg = "data\\image.dat";


int diff(const pixel& px1, const pixel& px2) {
	return abs(px1.r - px2.r) + abs(px1.b - px2.b) + abs(px1.g - px2.g);
}

struct rectangle {

	uint16_t bottomRightX = 0;
	uint16_t bottomRightY = 0;
	uint16_t topLeftX = 0;
	uint16_t topLeftY = 0;
	rectangle() {}
	rectangle(uint16_t tlx, uint16_t tly, uint16_t brx, uint16_t bry) {
		topLeftX = tlx;
		topLeftY = tly;
		bottomRightX = brx;
		bottomRightY = bry;
	}

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
	for (auto& row : data) {
		row = vector<pixel>(w);
	}

	for (int i = 0; i < h; ++i) {
		for (int j = 0; j < w; ++j) {
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

image cut(image &from, rectangle &to) {
	
	size_t h = to.bottomRightX - to.topLeftX + 1;

	size_t w = to.bottomRightY - to.topLeftY + 1;

	cout << "Rectangle: " << to.topLeftX << ' ' << to.topLeftY << ' ' << to.bottomRightX << ' ' << to.bottomRightY << endl;

	auto res = vector<vector<pixel>>(h);

	for (auto &line : res) {
		line = vector<pixel>(w);
	}

	for (size_t i = 0; i < h; i++) {
		for (size_t j = 0; j < w; j++) {

			res[i][j] = from[i + to.topLeftX][j + to.topLeftY];
		}
	}

	return res;
}


class Cutter {
private:
	image mainImg;

	std::queue<rectangle> rects;
	
public:
	Cutter(const string &pathToSmallImg) {
		mainImg = imread(bigImg);

		image smallImage = imread(pathToSmallImg);

		int bigH = mainImg.size();
		int bigW = mainImg[0].size();

		int smallH = smallImage.size();
		int smallW = smallImage[0].size();

		for (int i = 0; i < bigH - smallH; i++) 
		{
			for (int j = 0; j < bigW - smallW; j++) 
			{
				rects.push(rectangle(i, j, i + smallH - 1, j + smallW - 1));
			}
		}
	}

	bool operator()(rectangle &result) {
		if (rects.empty())
		{
			return false;
		}

		rectangle curr = rects.front();

		rects.pop();

		result = curr;

		return true;
	}
};

void processImage(const string& small, const string& smallTo) {
	graph g;

	image mBigImage = imread(bigImg);

	image mSmallImage = imread(small);

	source_node<rectangle> imgCutter(g, Cutter(small), false);

	buffer_node<rectangle> buffer(g);

	function_node<rectangle, std::tuple<rectangle, int64_t>> worker(g, unlimited,
		[&](const rectangle& r) {
		long long sum = 0;
		int h = mSmallImage.size();
		int w = mSmallImage.front().size();
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				sum += diff(mBigImage[r.topLeftX + i][r.topLeftY + j], mSmallImage[i][j]);
			}
		}

		return std::make_tuple(r, sum);
	}
	);

	buffer_node<std::tuple<rectangle, int64_t>> res(g);
	int64_t min = std::numeric_limits<int64_t>::max();

	rectangle curMin;

	function_node<std::tuple<rectangle, int64_t>> worker2(g, 1, 
	[&](std::tuple<rectangle, int64_t> t) {

		rectangle &rect = std::get<0>(t);

		int64_t val = std::get<1>(t);

		if (val < min) {

			min = val;

			curMin = rect;
		}

	});

	make_edge(imgCutter, buffer);

	make_edge(buffer, worker);

	make_edge(worker, res);

	make_edge(res, worker2);

	imgCutter.activate();

	g.wait_for_all();

	imwrite(cut(mBigImage, curMin), smallTo);
}

int main() {
	processImage("data\\cheer.dat", "data\\res_cheer.dat");
	processImage("data\\hat.dat", "data\\res_hat.dat");
	processImage("data\\chicken.dat", "data\\res_chicken.dat");
}