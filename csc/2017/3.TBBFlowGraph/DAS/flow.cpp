#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <limits>

#include <tbb/concurrent_queue.h>
#include <tbb/flow_graph.h>

using namespace std;
using namespace tbb::flow;

struct pixel {
	uint8_t r;
	uint8_t g;
	uint8_t b;

	pixel(uint8_t _r, uint8_t _g, uint8_t _b) : r(_r), g(_g), b(_b){}
	pixel(){}

	pixel operator-(pixel & p) {
		uint8_t dr = abs(r - p.r);
		uint8_t dg = abs(g - p.g);
		uint8_t db = abs(b - p.b);
		return pixel(dr, dg, db);
	}
	
	uint8_t reduce(){
		return (int)r + (int)g + (int)b;
	}
};

using image = vector<vector<pixel>>;

struct Area{
	image picture;
	int x; // x - cord of left corner
	int y; // y - cord of left corner
	long int difference;

	Area(const image & img, int _x, int _y, long int diff) : picture(img), x(_x), y(_y), difference(diff){}
	Area(){}
};

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

	uint32_t h, w, d;
	file.read(reinterpret_cast<char*>(&h), 4);
	file.read(reinterpret_cast<char*>(&w), 4);
	file.read(reinterpret_cast<char*>(&d), 4);

	auto data = vector<vector<pixel>>(h);
	for (auto& row : data) {
		row.resize(w);
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

struct Slicer{
private:
	tbb::concurrent_queue <Area> prebuffer;
public:
	Slicer(const image & img){
		int h_s = img.size();
		int w_s = img[0].size();
		image source = imread("image.dat");
		int h = source.size();
		int w = source.size();
		for (size_t i = 0; i < h - h_s; i++){
			for (size_t j = 0; j < w - w_s; j++)
			{
				auto first_iter = source.begin() + i;
				auto last_iter = first_iter + h_s;
				image sub_img(first_iter, last_iter);
				auto f = source[0].begin() + j;
				auto l = f + w_s;
				for (auto line : sub_img){
					vector<pixel> new_line(f, l);
					line = new_line;
				}
				prebuffer.push(Area(sub_img, i, j, 0));
			}
		}
	}
	bool operator()(Area & area){
		return prebuffer.try_pop(area);
	}
};


int main() {
	graph g;
	string  filenames[3] = { "cheer.dat", "chicken.dat", "hat.dat" };


	for (string filename : filenames){
		image sample;
		image pattern = imread(filename);
		static Area seek(sample, -1, -1, numeric_limits<long int>::max());

		// defining nodes

		source_node<Area> slicer_node(g, // node for slicing large image
			Slicer(pattern), false);

		buffer_node<Area> buffering_node(g); // node for storing results of slicing


		function_node<Area, Area> difference_node(g, unlimited, // node for calculating difference
			[&pattern](Area area){
			image img2 = area.picture;
			int h = pattern.size();
			int w = pattern[0].size();
			long int difference = 0;
			for (size_t i = 0; i < h; i++){
				for (size_t j = 0; j < w; j++){
					difference += (pattern[i][j] - img2[i][j]).reduce();
				}
			}
			area.difference = difference;
			return area;
		}
		);

		function_node<Area, continue_msg> result_node(g, serial, // node for storing result pic
			[](Area area){
			long int current_difference = area.difference;
			if (seek.difference > current_difference){
				seek.difference = current_difference;
				seek = area;
			}
			return;
		}
		);

		// defining edges
		make_edge(slicer_node, buffering_node);

		make_edge(buffering_node, difference_node);
		make_edge(difference_node, result_node);


		slicer_node.activate();
		g.wait_for_all();
		cout << "Left corner coordinates for " << filename << " : " << seek.x << " " << seek.y << endl;
		string result_filename = "result_" + filename;
		imwrite(seek.picture, result_filename);
	}
	return 0;
}