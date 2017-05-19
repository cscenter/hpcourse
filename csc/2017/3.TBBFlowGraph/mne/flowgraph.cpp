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

int pix_diff(pixel a, pixel b) {
  return abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b);
}

using image = vector<vector<pixel>>;
using point = tuple<int, int>;

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

class cutter {
  queue<point> to_produce;
  
public:
  cutter(point sample_shape, point giant_shape) {
    int small_height = get<0>(sample_shape);
    int small_width = get<1>(sample_shape);
    int giant_height = get<0>(giant_shape);
    int giant_width = get<1>(giant_shape);
    for (int i = 0; i < giant_height - small_height; ++i) {
      for (int j = 0; j < giant_width - small_width; ++j) {
        to_produce.push(make_tuple(i, j));  
      }
    }
  }
  
  bool operator()(point &result) {
    if (!to_produce.empty()) {
      result = to_produce.front();
      to_produce.pop();
      return true;
    } else {
      return false;
    }
  }
};

class min_reducer {
  tuple<int, point> &min;
public:
  min_reducer( tuple<int, point> &s ) : min(s) {}
  
  tuple<int, point> operator()(tuple<int, point> diff_pair) {
    min = get<0>(min) > get<0>(diff_pair) ? diff_pair : min;
    //cout << get<0>(min) << '\n';
    return min;
  }
  
  tuple<int, point> get_min() {
    return min;
  };
};

image extract(image &source, point position, point shape) {
  int height = get<0>(shape);
  int width = get<1>(shape);
  int y = get<0>(position);
  int x = get<1>(position);
  
  auto result = vector<vector<pixel>>(height);
  for (auto& row: result) {
    row.resize(width);
  }

  for (int i = 0; i < height; ++i) {
    for (int j = 0; j < width; ++j) {
      result[i][j] = source[y + i][x + j];
    }
  }

  return result;
}

void do_it(string giant_path, string sample_path, string out_path) {
  graph s;
  image giant = imread(giant_path);
  image sample = imread(sample_path);
  
  point giant_shape = make_tuple(giant.size(), giant[0].size());
  point sample_shape = make_tuple(sample.size(), sample[0].size());
  
  source_node<point> cutter_node( s, cutter(sample_shape, giant_shape), false);
  buffer_node<point> after_cutter_buff( s );
  
  function_node<point, tuple<int, point>> diff_node( s, unlimited, [giant, sample, sample_shape] (point position) {
    int height = get<0>(sample_shape);
    int width = get<1>(sample_shape);
    int y = get<0>(position);
    int x = get<1>(position);
    
    int dist = 0;
    for (int i = 0; i < height; ++i) {
      for (int j = 0; j < width; ++j) {
        dist += pix_diff(sample[i][j], giant[y + i][x + j]);
      }
    }
    return make_tuple(dist, position);
  });
  
  buffer_node<tuple<int, point>> after_diff_buff( s );
  
  tuple<int, point> min = make_tuple(INT_MAX, make_tuple(0, 0));
  function_node<tuple<int, point>, tuple<int, point>> min_reduce_node(s, 1, min_reducer(min));
  
  make_edge(cutter_node, after_cutter_buff);
  make_edge(after_cutter_buff, diff_node);
  make_edge(diff_node, after_diff_buff);
  make_edge(after_diff_buff, min_reduce_node);
  
  cutter_node.activate();
  s.wait_for_all();
  
  cout << "Input file: " << sample_path << endl;
  cout << "Found sample: " << out_path << endl;
  cout << "L1 distance: " << get<0>(min) << endl;
  cout << "Position: " << get<0>(get<1>(min)) << ", "<< get<1>(get<1>(min)) << endl;
  
  imwrite(extract(giant, get<1>(min), sample_shape), out_path);
}

int main() {
  do_it("../data/image.dat", "../data/chicken.dat", "chicken_found.dat");
  do_it("../data/image.dat", "../data/hat.dat", "hat_found.dat");
  do_it("../data/image.dat", "../data/cheer.dat", "cheer_found.dat");
}
