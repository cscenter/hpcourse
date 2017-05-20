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

struct rectangle {
    int lx, ly, rx, ry;
};

int uabs(uint8_t a, uint8_t b) {
    return (a >= b ? a - b : b - a);
}

int diffPixels(pixel p1, pixel p2) {
    return uabs(p1.r, p2.r) + uabs(p1.g, p2.g) + uabs(p1.b, p2.b); 
}

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

image rectangleToImage(rectangle r, image im) {
    image res;
    res.resize(r.rx - r.lx, vector<pixel>(r.ry - r.ly));

    for (int x = 0; x < r.rx - r.lx; x++) {
        for (int y = 0; y < r.ry - r.ly; y++) {
            res[x][y] = im[r.lx + x][r.ly + y];
        }
    }

    return res;
}

void findImage(const std::string& in) {
    int prefix = in.find(".");
    std::string out = in.substr(0, prefix) + "_result" + in.substr(prefix, in.size() - prefix);
    //cout << out << endl;

    graph g;

    image bigImage = imread("data/image.dat");
    image smallImage = imread(in);
     
    int n = (int)bigImage.size();
    int m = (int)bigImage[0].size();

    int szx = (int)smallImage.size();
    int szy = (int)smallImage[0].size();

    int posx = 0;
    int posy = -1;

    cout << "n= " << n << ", m=" << m << '\n';
    cout << "find " << szx << "*" << szy << '\n';

    source_node<rectangle> rectangleGenerator(g, [&](rectangle &r) -> bool {
        posy++;

        if (posy + szy > m) {
            posx++;
            posy = 0;

            //cout << "at x = " << posx << '\n';
        }

        r = rectangle {posx, posy, posx + szx, posy + szy};
           
        return (posx + szx <= n);  
    }, false);

    buffer_node<rectangle> rectangleBuffer(g);
     
    function_node<rectangle, pair<long long, rectangle>> diffCounter(g, unlimited, 
        [&](const rectangle &r) -> pair<long long, rectangle> {
            long long diff = 0;
            for (int x = r.lx; x < r.rx; x++) {
                for (int y = r.ly; y < r.ry; y++) {
                    diff += diffPixels(smallImage[x - r.lx][y - r.ly], bigImage[x][y]);
                }
            }
                
            return make_pair(diff, r);      
        } );

    long long minDiff = (long long)szx * szy * (1 << 8) * 3 + 1;
    rectangle best = {0, 0, szx, szy};
                                                                    
    function_node<pair<long long, rectangle>, bool> bestGetter(g, 1,
        [&](const pair<long long, rectangle> &p) -> bool {
            if (p.first < minDiff) {
                minDiff = p.first;
                best = p.second;
            }

            return true;
        } );

    make_edge(rectangleGenerator, rectangleBuffer);
    make_edge(rectangleBuffer, diffCounter);
    make_edge(diffCounter, bestGetter);

    rectangleGenerator.activate();
    g.wait_for_all();    
    
    cout << "Best difference: " << minDiff << '\n';
    cout << "Best rectangle: " << "[" << best.lx << ".." << best.rx << "] x [" << best.ly << ".." << best.ry << "]\n";
    cout << "Printing neighborhood...\n";

    rectangle nbest = best;
    const int DELTA = 7;

    nbest.lx = max(0, nbest.lx - DELTA);
    nbest.ly = max(0, nbest.ly - DELTA);
    nbest.rx = min(n, nbest.rx + DELTA);
    nbest.ry = min(m, nbest.ry + DELTA);

    image answer = rectangleToImage(nbest, bigImage);
    imwrite(answer, out);     

    cout << "Finished.\n";
}

int main() {
    findImage("data/cheer.dat");
    findImage("data/chicken.dat");
    findImage("data/hat.dat");
    
    return 0;
}
