#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>

#include <tbb/flow_graph.h>
#include <tbb/parallel_for.h>

using namespace std;
using namespace tbb::flow;

const string pathToBigImg = "data/image.dat";
struct pixel {
	uint8_t r;
	uint8_t g;
	uint8_t b;
};


int differenceBetween(const pixel& lhs, const pixel& rhs) {
	// uint -> int -> abs
	return abs((int)lhs.r - (int)rhs.r)
			+ abs((int)lhs.b - (int)rhs.b)
			+ abs((int)lhs.g - (int)rhs.g);
}

struct rect {
	rect(uint16_t th, uint16_t tw, uint16_t bh, uint16_t bw) {
		topLeftH = th;
		topLeftW = tw;
		bottomRightH = bh;
		bottomRightW = bw;
	}

	rect(){}

	friend std::ostream& operator<< (std::ostream& stream, const rect& r) {
		stream << r.topLeftH << " " << r.topLeftW << " " << r.bottomRightH << " " << r.bottomRightW << "\n";
		return stream;
	}

	uint16_t topLeftH = 0;
	uint16_t topLeftW = 0;
	uint16_t bottomRightH = 0;
	uint16_t bottomRightW = 0;
};

inline bool operator==(const rect& lhs, const rect& rhs) {
	return lhs.bottomRightH == rhs.bottomRightH && lhs.bottomRightW == rhs.bottomRightW
			&& lhs.topLeftW == rhs.topLeftW && lhs.topLeftW == rhs.topLeftW;
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
	size_t h = dstRect.bottomRightH - dstRect.topLeftH + 1;
	size_t w = dstRect.bottomRightW - dstRect.topLeftW + 1;
	cout << h << " " << w << " rect: " << dstRect;
	auto dstImg = vector<vector<pixel>>(h);
	for (auto &row : dstImg)
		row.resize(w);

	for (size_t i = 0; i < h; ++i) {
		for (size_t j = 0; j < w; ++j) {
			dstImg[i][j] = src[i + dstRect.topLeftH][j + dstRect.topLeftW];
		}
	}

	return dstImg;
}


class imageSlicer {
	std::queue<rect> mRects;
	image mBigImage;
public:
	imageSlicer(const string &pathToSmallImg) {
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

	bool operator()(rect &result) {
		if (mRects.empty())
			return false;

		rect currRect = mRects.front();
		mRects.pop();
//		if (currRect == rect(0,0,0,0)) {
//			cout << "WTF ??!!!";
//		}

		result = currRect;
//		cout << result;
		return true;
	}
};

void handleSmallImg(const string& smallImgPath, const string& resSmallImgPath) {
	graph flowGraph;

	image mBigImage = imread(pathToBigImg);
	image mSmallImage = imread(smallImgPath);

	source_node<rect> imageToRegions(flowGraph, imageSlicer(smallImgPath), false);
	buffer_node<rect> regionsBuffer(flowGraph);
	function_node<rect, std::tuple<rect,int64_t>> worker(flowGraph, unlimited,
		[&](const rect& cr) {
			int64_t difference = 0;
			size_t h = mSmallImage.size();
			size_t w = mSmallImage.front().size();
			for (size_t i = 0; i < h; ++i) {
				for (size_t j = 0; j < w; ++j) {
					difference += differenceBetween(mBigImage[cr.topLeftH + i][cr.topLeftW + j], mSmallImage[i][j]);
				}
			}

//			cout << cr << difference << endl;

//			if (cr.topLeftH == 735 && cr.topLeftW == 1508) {
//				cout << difference << " " << std::numeric_limits<int64_t>::max() << " " << cr;
//			}

			return std::make_tuple(cr, difference);
		}
	);

	buffer_node<std::tuple<rect,int64_t>> resultBuffer(flowGraph);
	int64_t min = std::numeric_limits<int64_t>::max();
	rect mCurMinRect;
	function_node<std::tuple<rect,int64_t>> worker2(flowGraph, 1, [&](std::tuple<rect,int64_t> t) {
		rect &testRect = std::get<0>(t);
		int64_t testMin = std::get<1>(t);
		if (testMin < min) {
			min = testMin;
			mCurMinRect = testRect;
		}

//		if (testRect.topLeftH == 735 && testRect.topLeftW == 1508) {
//			cout << min << " " << std::numeric_limits<int64_t>::max() << " " << mCurMinRect;
//		}
	});

	make_edge(imageToRegions, regionsBuffer);
	make_edge(regionsBuffer, worker);
	make_edge(worker, resultBuffer);
	make_edge(resultBuffer, worker2);

	imageToRegions.activate();
	flowGraph.wait_for_all();

	imwrite(getRegionImg(mBigImage, mCurMinRect), resSmallImgPath);
}

int main() {
	handleSmallImg("data/cheer.dat","data/res_cheer.dat");
	handleSmallImg("data/hat.dat","data/res_hat.dat");
	handleSmallImg("data/chicken.dat","data/res_chicken.dat");
}