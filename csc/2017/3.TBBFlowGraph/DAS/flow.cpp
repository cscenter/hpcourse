#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <limits>

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
		uint8_t dr = r - p.r;
		uint8_t dg = g - p.g;
		uint8_t db = b - p.b;
		return pixel(dr, dg, db);
	}
	
	uint8_t reduce(){
		return r + g + b;
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


int main() {
	graph g;
	string filename;
	cin >> filename;
	//filename = "cheer.dat";

	static int i = 0;
	static int j = 0;

	image sample;
	static Area seek(sample, -1, -1, numeric_limits<long int>::max());

	// defining nodes

	function_node<string, image> reader_node(g, 1, 
		[](const string & filename){
			return imread(filename);
		}
	);

	write_once_node<image> repeating_node(g); // node to repeat pattern for comparing with slices 
	broadcast_node<image> broadcast_repeat(g); // broadcast to two nides: slicer and one of join ports

	function_node<image, Area> slicer_node(g, unlimited, // node for slicing large image
		[](const image & img){
			int h_s = img.size();
			int w_s = img[0].size();
			image source = imread("image.dat");
			int h = source.size();
			int w = source.size();
			if (i == h - h_s) i++;
			if (j == j - w_s) j++;
			auto first_iter = source.begin() + i;
			auto last_iter = first_iter + h_s;
			image sub_img(first_iter, last_iter);
			auto f = source[0].begin() + j;
			auto l = f + w_s;
			for (auto line : sub_img){
				vector<pixel> new_line(f, l);
				line = new_line;
			}
			return Area(sub_img, i, j, 0);
		}
	);

	buffer_node<Area> buffering_node(g); // node for storing results of slicing

	join_node<tuple<image, Area>, queueing> join(g); // joining slice and pattern image

	function_node<tuple<image, Area>, Area> difference_node(g, unlimited, // node for calculating distance
		[](tuple <image, Area> t){
		image img1 = get<0>(t);
		Area area = get<1>(t);
		image img2 = area.picture;
		int h = img1.size();
		int w = img1[0].size();
		long int difference = 0;
		for (size_t i = 0; i < h; i++){
			for (size_t j = 0; j < w; j++){
				difference += int((img1[i][j] - img2[i][j]).reduce());
			}
		}
		area.difference = difference;
		return area;
	}
	);
	
	function_node<Area> result_node(g, unlimited, // node for storing result pic
		[](const Area & area){
		long int current_difference = area.difference;
		if (seek.difference > current_difference){
			seek.difference = current_difference;
			seek = area;
		}
		return;
	}
	);
	
	function_node<continue_msg> writing_node(g, unlimited, // node for writing result picture
		[](const continue_msg &){
		imwrite(seek.picture, "result.dat");
	}
	);
	// defining edges
	
	make_edge(reader_node, repeating_node);
	make_edge(repeating_node, broadcast_repeat);
	make_edge(broadcast_repeat, slicer_node);
	make_edge(slicer_node, buffering_node);
	
	make_edge(buffering_node, input_port<1>(join));
	make_edge(broadcast_repeat, input_port<0>(join));

	make_edge(join, difference_node);
	make_edge(difference_node, result_node);
	make_edge(result_node, writing_node);

	reader_node.try_put(filename);
	g.wait_for_all();
	return 0;
}