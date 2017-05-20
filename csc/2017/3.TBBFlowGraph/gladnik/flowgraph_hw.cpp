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

struct rectangle {
	int x1;
	int x2;
	int y1;
	int y2;

	rectangle(int x1, int y1, int x2, int y2) : x1(x1), x2(x2), y1(y1), y2(y2) {}
	rectangle() {
		x1 = 0;
		x2 = 0;
		y1 = 0;
		y2 = 0;
	}
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
		for (auto& pix: row) {
			file.write(reinterpret_cast<const char*>(&pix.r), 1);
			file.write(reinterpret_cast<const char*>(&pix.g), 1);
			file.write(reinterpret_cast<const char*>(&pix.b), 1);
		}
	}
	file.close();
}

class RectanglesGenerator {
	list<rectangle> rectangles;
public:
	RectanglesGenerator(const image &fullImage, const image &target) {
		int fullWidth = fullImage.size();
		int fullHeight = fullImage[0].size();
		int targetWidth = target.size();
		int targetHeight = target[0].size();

		for (int i = 0; i < fullWidth - targetWidth; i++) {
			for (int j = 0; j < fullHeight - targetHeight; j++) {
				rectangles.push_back(rectangle(i, j, i + targetWidth, j + targetHeight));
			}
		}
	}

	bool operator()(rectangle &result) {
		if (rectangles.empty()) {
			return false;
		}
		result = rectangles.front();
		rectangles.pop_front();
		return true;
	}
};

class DiffCalculator {
	image full;
	image target;
public:
	DiffCalculator(const image &fullImage, const image &targetImage) {
		full = fullImage;
		target = targetImage;
	}

	pair<rectangle, int> operator()(const rectangle &currRect) {
		int diff = 0;
		int targetWidth = target.size();
		int targetHeight = target[0].size();
		for (int i = 0; i < targetWidth; i++) {
			for (int j = 0; j < targetHeight; j++) {
				pixel pFull = full[currRect.x1 + i][currRect.y1 + j];
				pixel pTarget = target[i][j];

				diff += abs((int)pFull.r - (int)pTarget.r)
								+ abs((int)pFull.b - (int)pTarget.b)
								+ abs((int)pFull.g - (int)pTarget.g);
			}
		}
		return pair<rectangle, int> (currRect, diff);
	}
};

class MatchFinder {
	rectangle& result;
	int currMin = INT_MAX;
public:
	MatchFinder(rectangle& result) : result(result) {}

	rectangle operator()(pair<rectangle, int> comparationRes) {
		rectangle& rect = comparationRes.first;
		int diff = comparationRes.second;
		if (diff < currMin) {
			currMin = diff;
			result = rect;
		}
		return result;
	}
};

image getImageForRectangle(const image &fullImage, const rectangle &rect) {
	int width = rect.x2 - rect.x1;
	int height = rect.y2 - rect.y1;
	image result = vector<vector<pixel>>(width);
	for (auto &row : result)
		row.resize(height);
	for (int i = 0; i < width; i++) {
		for (int j = 0; j < height; j++) {
			result[i][j] = fullImage[i + rect.x1][j + rect.y1];
		}
	}
	return result;
}


image findSmall(image &fullImage, image &target) {
	graph g;

	rectangle result;

	source_node<rectangle> generateRectangles(g, RectanglesGenerator(fullImage, target), false);
	buffer_node<rectangle> rectanglesBuffer(g);
	function_node<rectangle, pair<rectangle, int>> rectanglesComparator(g, unlimited, DiffCalculator(fullImage, target));
	buffer_node<pair<rectangle, int>> comparedBuffer(g);
	function_node<pair<rectangle, int>, rectangle> matchFinder(g, 1, MatchFinder(result));

	make_edge(generateRectangles, rectanglesBuffer);
	make_edge(rectanglesBuffer, rectanglesComparator);
	make_edge(rectanglesComparator, comparedBuffer);
	make_edge(comparedBuffer, matchFinder);

	generateRectangles.activate();
	g.wait_for_all();

	cout << "res coordinates: " << result.x1 << " " << result.y1 << " " << result.x2 << " " << result.y2 << endl;
	return getImageForRectangle(fullImage, result);
}

int main() {
	image fullImage = imread("data/image.dat");

	image hat = imread("data/hat.dat");
	imwrite(findSmall(fullImage, hat), "hat_res.dat");

	image cheer = imread("data/cheer.dat");
	imwrite(findSmall(fullImage, cheer), "cheer_res.dat");

	image chicken = imread("data/chicken.dat");
	imwrite(findSmall(fullImage, chicken), "chicken_res.dat");

	return 0;
}
