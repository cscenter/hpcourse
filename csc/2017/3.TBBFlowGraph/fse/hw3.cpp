#include <iostream>
#include <fstream>
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

image imread(const std::string &path) {
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
    file.read(reinterpret_cast<char *>(&h), 4);
    file.read(reinterpret_cast<char *>(&w), 4);
    file.read(reinterpret_cast<char *>(&d), 4);

    auto data = vector<vector<pixel>>(h);
    for (auto &row: data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = pixel {uint8_t(pix[0]),
                                uint8_t(pix[1]),
                                uint8_t(pix[2])};
        }
    }

    return data;
}

void imwrite(const image &source, const string &path) {
    int h = source.size();
    int w = source[0].size();
    int d = 3;

    ofstream file(path, ios::binary);

    if (!file.is_open()) {
        cerr << "Can not open the file" << endl;
        throw invalid_argument(path);
    }

    file.write(reinterpret_cast<char *>(&h), 4);
    file.write(reinterpret_cast<char *>(&w), 4);
    file.write(reinterpret_cast<char *>(&d), 4);

    for (auto &row : source) {
        for (auto &pix: row) {
            file.write(reinterpret_cast<const char *>(&pix.r), 1);
            file.write(reinterpret_cast<const char *>(&pix.g), 1);
            file.write(reinterpret_cast<const char *>(&pix.b), 1);
        }
    }
    file.close();
}


class graph_image{

    struct frame {
        frame(){}
        long x, y;
        frame(long x,long y) {
            this->x = x; this->y = y;
        }
    };

    long diff_rgb(pixel& l, pixel& r ){
        return abs(l.r-r.r)+abs(l.g-r.g)+abs(l.b-r.b);
    }

    graph g;
    image& big_im;
    image small_im;
    string& out_file;

    long diff;
    const long h, w;
    frame res;


    struct generator{
        list<frame> list_fr;
        generator(int x, int y){
            for (int i = 0; i < x; ++i)
                for (int j = 0; j < y; ++j)
                    list_fr.push_back(frame(i, j));
        }

        bool operator()(frame & tmp_fr){
            if(list_fr.empty())
                return false;
            tmp_fr = list_fr.front();
            list_fr.pop_front();
            return true;
        }
    };

    image copy_fr(frame frame1) {
        image image_out(h, std::vector<pixel>(w, {0, 0, 0}));
        for (int row = 0; row < h; row++)
            for (int col = 0; col < w; col++)
                image_out[row][col] = big_im[frame1.x + row][frame1.y + col];

        return image_out;
    }


public:
    graph_image(image& im1, string& in, string& out ): big_im(im1), small_im(imread(in)), out_file(out),
                                                       h(small_im.size()), w(small_im[0].size()){
        long x,y;
        source_node<frame> generator_fr(g, generator((int) (big_im.size() - h), (int) (big_im[0].size() - w)), false);
        buffer_node<frame> buff_frame(g);
        function_node<frame, pair<frame, long>>diff_frame(g, unlimited, [&] (frame tmp_fr) {

            long sum = 0;
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    sum += diff_rgb(big_im[tmp_fr.x+row][tmp_fr.y+col], small_im[row][col]);
                }
            }

            return make_pair(tmp_fr, sum);
        });

        buffer_node<pair<frame, long>> buff_diff(g);

        diff = 255 * 3 * h * w + 1;

        function_node<pair<frame, long>> min_buff(g, serial, [&] (pair<frame, long> tmp_diff) {

            if (tmp_diff.second < diff) {
                res = tmp_diff.first;
                diff = tmp_diff.second;
            }
        });

        make_edge(generator_fr, buff_frame);
        make_edge(buff_frame, diff_frame);
        make_edge(diff_frame, buff_diff);
        make_edge(buff_diff, min_buff);
        generator_fr.activate();
        g.wait_for_all();
        std::cout <<out_file << ' ' << res.x<<' '<<res.y<<' '<< diff<<'\n';
        imwrite(copy_fr(res), out_file);
    }
};


int main() {
    string big_im = "../data/image.dat";
    vector<string> small_in = {"../data/chicken.dat", "../data/hat.dat", "../cheer.dat"};
    vector<string> small_out = {"../data/chicken_out.dat", "../data/hat_out.dat", "../cheer_out.dat"};
    image p = imread(big_im);
    for (int i = 0; i < 3; ++i) {
        graph_image go(p, small_in[i], small_out[i]);
    }

    return 0;
}
