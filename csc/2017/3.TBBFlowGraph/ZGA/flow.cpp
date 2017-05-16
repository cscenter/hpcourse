#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>

#include <tbb/flow_graph.h>
#include <tbb/parallel_for.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
	uint8_t r;
	uint8_t g;
	uint8_t b;
};


int differenceBetween(const pixel& lhs, const pixel& rhs) {
	return abs(lhs.r - rhs.r
			   + lhs.b - rhs.b
			   + lhs.g - rhs.g);
}

struct rect {
	rect(uint16_t tx, uint16_t ty, uint16_t bx, uint16_t by) {
		topLeftX = tx;
		topLeftY = ty;
		bottomRightX = bx;
		bottomRightY = by;
	}

	uint16_t topLeftX = 0;
	uint16_t topLeftY = 0;
	uint16_t bottomRightX = 0;
	uint16_t bottomRightY = 0;
};

inline bool operator==(const rect& lhs, const rect& rhs) {
	return lhs.bottomRightX == rhs.bottomRightX && lhs.bottomRightY == rhs.bottomRightY
			&& lhs.topLeftX == rhs.topLeftX && lhs.topLeftY == rhs.topLeftY;
}


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

image getRegionImg(const image &src, const rect &dstRect) {
	size_t h = dstRect.bottomRightX - dstRect.topLeftX + 1;
	size_t w = dstRect.bottomRightX - dstRect.topLeftX + 1;
	auto dstImg = vector<vector<pixel>>(h);
	for (auto &row : dstImg)
		row.resize(w);

	for (size_t i = 0; i < h; ++i) {
		for (size_t j = 0; j < w; ++j) {
			dstImg[i][j] = src[i + dstRect.topLeftX][j + dstRect.topLeftY];
		}
	}

	return dstImg;
}


class imageSlicer {
	std::queue<rect> mRects;
	image mBigImage;
public:
	imageSlicer(const string &pathToSmallImg) {
		const string pathToBigImg = "data/image.dat";
		mBigImage = imread(pathToBigImg);
		image smallImage = imread(pathToSmallImg);
		const size_t h_small = smallImage.size();
		const size_t w_small = smallImage.front().size();
		const size_t h_big = mBigImage.size();
		const size_t w_big = mBigImage.front().size();

		for (size_t i = 0; i < h_big - h_small; ++i) {
			for (size_t j = 0; j < w_big - w_small; ++j) {
				rect r(i, j, i + h_small - 1, j + w_small - 1);
				mRects.push(r);
			}
		}
	}

	bool operator()(image &result) {
		if (mRects.empty())
			return false;

		rect currRect = mRects.front();
		mRects.pop();
		result = getRegionImg(mBigImage, currRect);
//		cout << "imageSlicer" << currRect.topLeftX << " " << currRect.topLeftY << endl;
		return true;
	}
};

class smallImageGenerator {
	image mImage;

public:
	smallImageGenerator(const string &pathToSmallImg) : mImage(imread(pathToSmallImg)) {}

	bool operator()(image &result) {
		result = mImage;
//		cout << "smallImageGenerator" << endl;
		return true;
	}
};

class minHandler {
	int64_t min = std::numeric_limits<int64_t>::max();
	image curImage;

public:
	void operator()(std::tuple<image,int64_t> t) {
		std::cout << "minHandler" << min << " ";
		int64_t testMin = std::get<1>(t);
		if (testMin < min) {
			min = testMin;
			curImage = std::get<0>(t);
		}

		std::cout << min << std::endl;
	}

	image getImage() {
		return curImage;
	}
};

void handleSmallImg(const string& smallImgPath, const string& resSmallImgPath) {
	graph flowGraph;
	source_node<image> imageToRegions(flowGraph, imageSlicer(smallImgPath), false);
	source_node<image> smallImgGenerator(flowGraph, smallImageGenerator(smallImgPath), false);
	buffer_node<image> regionsBuffer(flowGraph);
	buffer_node<image> smallImagesBuffer(flowGraph);
	join_node<tbb::flow::tuple<image,image>> joinNode(flowGraph);
	function_node<tbb::flow::tuple<image,image>, std::tuple<image,int64_t>> worker(flowGraph, unlimited,
		[](tbb::flow::tuple<image,image> imgPair) {
			image first = (vector<vector<pixel>> &&) std::get<0>(imgPair);
			image second = (vector<vector<pixel>> &&) std::get<1>(imgPair);
			int64_t difference = 0;
			size_t h = first.size();
			size_t w = first.front().size();
			for (size_t i = 0; i < h; ++i) {
				for (size_t j = 0; j < w; ++j) {
					difference += differenceBetween(first[i][j], second[i][j]);
				}
			}

//			cout << "worker" << difference;

			return std::make_tuple(first, difference);
		}
	);

	buffer_node<std::tuple<image,int64_t>> resultBuffer(flowGraph);
	minHandler mMinHandler;
	function_node<std::tuple<image,int64_t>> worker2(flowGraph, 1, mMinHandler);

	make_edge(imageToRegions, regionsBuffer);
	make_edge(smallImgGenerator, smallImagesBuffer);
	make_edge(regionsBuffer, input_port<0>(joinNode));
	make_edge(smallImagesBuffer, input_port<1>(joinNode));
	make_edge(joinNode, worker);
	make_edge(worker, resultBuffer);
	make_edge(resultBuffer, worker2);

	imageToRegions.activate();
	smallImgGenerator.activate();
	flowGraph.wait_for_all();

	imwrite(mMinHandler.getImage(), resSmallImgPath);
}

int main() {
	handleSmallImg("data/cheer.dat","data/res_cheer.dat");
}