
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

image imread(const string& path) {
	if (path.compare(path.size() - 4, 4, ".dat") != 0) {
		cerr << "Can read only prepared .dat files!" << endl;
		throw invalid_argument(path);
	}

	ifstream file(path, ios::binary | ios::in);

	uint32_t h, w, d;
	file.read(reinterpret_cast<char*>(&h), 4);
	file.read(reinterpret_cast<char*>(&w), 4);
	file.read(reinterpret_cast<char*>(&d), 4);

	auto data = vector<vector<pixel> >(h);
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

image source_img;
uint32_t sourse_height;
uint32_t sourse_width;

void imwrite(const image& source, const string& path) {
	uint32_t h = source.size();
	uint32_t w = source[0].size();
	uint32_t d = 3;
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

struct reader {
	image operator()(const string& path) {
		return imread(path);
	}
};

struct my_rectangle {
	tuple<uint32_t, uint32_t> coords;
	tuple<uint32_t, uint32_t> sizes;

	my_rectangle(tuple<uint32_t, uint32_t> coords, tuple<uint32_t, uint32_t> sizes)
		: coords(coords)
		, sizes(sizes)
	{
	}

	my_rectangle()
		: coords(make_tuple(0, 0))
		, sizes(make_tuple(0, 0))
	{	
	}
};

class generator {
public:
	generator(uint32_t height, uint32_t width) {
		uint32_t h_steps = sourse_height - height;
		uint32_t w_steps = sourse_width - width;

		for (uint32_t i = 0; i < h_steps + 1; ++i) {
			for (uint32_t j = 0; j < w_steps + 1; ++j) {
				rectangles.push_back(new my_rectangle(make_tuple(i, j), make_tuple(height, width)));
			}
		}
	}

	bool operator()(my_rectangle& rect) {
		if (!rectangles.empty()) {
			rect = *rectangles.back();
			rectangles.pop_back();
			return true;
		}
		return false;
	}
	
private:
	vector<my_rectangle*> rectangles;
};

class difference {
public:
	difference(const image& to_compare)
		: to_compare(to_compare)
	{
	}

	tuple<uint32_t, tuple<uint32_t, uint32_t> > operator()(my_rectangle curr_rect) {
		uint32_t diff = 0;
		uint32_t curr_height = get<0>(curr_rect.sizes);
		uint32_t curr_width = get<1>(curr_rect.sizes);

		uint32_t curr_y = get<0>(curr_rect.coords);
		uint32_t curr_x = get<1>(curr_rect.coords);

		for (uint32_t i = 0; i < curr_height; ++i) {
			for (uint32_t j = 0; j < curr_width; ++j) {
				diff += abs(source_img[curr_y + i][curr_x + j].r - to_compare[i][j].r);
				diff += abs(source_img[curr_y + i][curr_x + j].g - to_compare[i][j].g);
				diff += abs(source_img[curr_y + i][curr_x + j].b - to_compare[i][j].b);
			}
		}
		return make_tuple(diff, curr_rect.coords);
	}	

private:
	image to_compare;
};

class min_difference {
public:
	min_difference(tuple<uint32_t, tuple<uint32_t, uint32_t> > &s) : my_min(s) {}
	tuple<uint32_t, tuple<uint32_t, uint32_t> > operator()(tuple<uint32_t, tuple<uint32_t, uint32_t> > x) {
		if (get<0>(my_min) > get<0>(x)) {
			my_min = x;
		}
		return my_min;
	}

private:
	tuple<uint32_t, tuple<uint32_t, uint32_t> > &my_min;
};

void write_result(tuple<uint32_t, tuple<uint32_t, uint32_t> > result, const image& to_compare, const string& path) {
	cout << "Min difference: " << get<0>(result) << endl;
	uint32_t y_coord = get<0>(get<1>(result)), x_coord = get<1>(get<1>(result));
	cout << "Coords are: (" << y_coord << ", " << x_coord << ")" << endl;

	int h = to_compare.size();
	int w = to_compare[0].size();
	int d = 3;

	auto data = vector<vector<pixel>>(h);
	for (auto& row : data) {
		row.resize(w);
	}

	for (int i = 0; i < h; ++i) {
		for (int j = 0; j < w; ++j) {
			data[i][j] = source_img[i + y_coord][j + x_coord];
		}
	}

	imwrite(data, path + "_my_res.dat");
}

void make_graph(const string& path) {
	tuple<uint32_t, tuple<uint32_t, uint32_t> > result = make_tuple(0, make_tuple(0, 0));
	image to_compare = imread(path);

	uint32_t height = to_compare.size();
	uint32_t width = to_compare[0].size();

	graph g;
	cout << "Create empty graph" << endl;

	source_node<my_rectangle> generator_node(g, generator(height, width), false);
	buffer_node<my_rectangle> rect_buffer_node(g);
	function_node<my_rectangle, tuple<uint32_t, tuple<uint32_t, uint32_t> > >
		difference_node(g, unlimited, difference(to_compare));
	buffer_node<tuple<uint32_t, tuple<uint32_t, uint32_t> > > diff_buffer(g);
	function_node<tuple<uint32_t, tuple<uint32_t, uint32_t> >, tuple<uint32_t, tuple<uint32_t, uint32_t> >>
		min_difference_node(g, serial, min_difference(result));

	cout << "Create nodes" << endl;

	make_edge(generator_node, rect_buffer_node);
	make_edge(rect_buffer_node, difference_node);
	make_edge(difference_node, diff_buffer);
	make_edge(diff_buffer, min_difference_node);

	cout << "Create edges" << endl;

	generator_node.activate();
	g.wait_for_all();

	cout << "Write res" << endl;

	write_result(result, to_compare, path);
}

int main() {
	source_img = imread("data\\image.dat");
	sourse_height = source_img.size();
	sourse_width = source_img[0].size();

	cout << "Start for hat" << endl;
	make_graph("data\\hat.dat");
	cout << "Start for chicken" << endl;
	make_graph("data\\chicken.dat");
	cout << "Start for cheer" << endl;
	make_graph("data\\cheer.dat");
	
	return 0;
}