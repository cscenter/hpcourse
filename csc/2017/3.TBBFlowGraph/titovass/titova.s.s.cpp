#include <iostream>
#include <fstream>
#include <algorithm>
#include <vector>
#include <queue>
#include <array>

#include <tbb/flow_graph.h>
#include <tbb/parallel_for.h>

using namespace std;
using namespace tbb::flow;


struct pixel
{
    uint8_t r;
    uint8_t g;
    uint8_t b;
    
    uint8_t operator-(pixel const & other)
    {
        return abs(r - other.r) + abs(g - other.g) + abs(b - other.b);
    }
};

using image = vector<vector<pixel>>;

image b_im;
image s_im;


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


struct part_im
{
    part_im() : tl_h(0), tl_w(0), br_h(0),  br_w(0) {}
    part_im(size_t lh, size_t lw, size_t rh,  size_t rw) : tl_h(lh), tl_w(lw), br_h(rh), br_w(rw) {}
    size_t tl_h, tl_w, br_h, br_w;
    
};


struct cut_im
{
    
public:
    cut_im()
    {
        size_t b_h = b_im.size();
        size_t b_w = b_im.front().size();
        size_t s_h = s_im.size();
        size_t s_w = s_im.front().size();
        
        for (int i = 0; i < b_h - s_h; i++)
        {
            for (int j = 0; j < b_w - s_w; j++)
            {
                parts.push(part_im(i, j, i + s_h - 1, j + s_w - 1));
            }
        }
    }
    
    bool operator()(part_im &result)
    {
        if (parts.empty())
        {
            return false;
        }
        result = parts.front();
        parts.pop();
        return true;
    }
    
private:
    queue<part_im> parts;
};


struct calc_diff_func
{
    std::tuple<part_im, size_t> operator()(const part_im& cur)
    {
        size_t cur_diff = 0;
        size_t h = s_im.size();
        size_t w = s_im.front().size();
        for (size_t i = 0; i < h; i++)
        {
            for (size_t j = 0; j < w; j++)
            {
                cur_diff += b_im[i + cur.tl_h][j + cur.tl_w] - s_im[i][j];
            }
        }
        return std::make_tuple(cur, cur_diff);
    }
};


void handleSmallImg(const string& b_p, const string& s_p, const string& r_p) {
    
    graph gr;
    
    s_im = imread(s_p);
    b_im = imread(b_p);
    
    part_im min_part;
    size_t min = std::numeric_limits<size_t>::max();
    
    source_node<part_im> im_to_parts(gr, cut_im(), false);
    buffer_node<part_im> parts_buf(gr);
    function_node<part_im, std::tuple<part_im, size_t>> calc_diff(gr, unlimited, calc_diff_func());
    buffer_node<std::tuple<part_im, size_t>> res_buf(gr);
    
    function_node<std::tuple<part_im, size_t>> find_min_diff(gr, 1, [&](std::tuple<part_im, size_t> cur)
    {
         if (std::get<1>(cur) < min)
         {
             min = std::get<1>(cur);
             min_part = std::get<0>(cur);
         }
    });
    
    make_edge(im_to_parts, parts_buf);
    make_edge(parts_buf, calc_diff);
    make_edge(calc_diff, res_buf);
    make_edge(res_buf, find_min_diff);
    
    im_to_parts.activate();
    gr.wait_for_all();
    
    size_t lh = min_part.tl_h;
    size_t rh = min_part.br_h;
    size_t lw = min_part.tl_w;
    size_t rw = min_part.br_w;
    long s1 = abs((long)lh - (long)rh);
    long s2 = abs((long)lw - (long)rw);
    vector<vector<pixel>> res (s1 + 1, vector<pixel>(s2 + 1));
    
    for (size_t i = 0; i <= s1; ++i)
    {
        for (size_t j = 0; j <= s2; ++j)
        {
            res[i][j] = b_im[lh + i][lw + j];
        }
    }
    imwrite(res, r_p);
}

int main() {
    handleSmallImg("data/image.dat", "data/cheer.dat","data/res_cheer.dat");
    handleSmallImg("data/image.dat", "data/hat.dat","data/res_hat.dat");
    handleSmallImg("data/image.dat", "data/chicken.dat","data/res_chicken.dat");
}