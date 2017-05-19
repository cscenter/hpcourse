// Serge G. Shulman 19.05.2017
#include <iostream>
#include <fstream>
#include <algorithm>
#include <array>
#include <cmath>
#include <tbb/flow_graph.h>

// Note: x is for height and y is for width
using namespace tbb::flow;

struct pixel 
{
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

struct frame
{
	frame()
	: x(0), y(0), w(0), h(0)
	{}
	frame(uint32_t x, uint32_t y, uint32_t w, uint32_t h)
	: x(x), y(y), w(w), h(h)
	{}
	
	uint32_t x;
	uint32_t y;
	uint32_t w;
	uint32_t h;
};

using image = std::vector<std::vector<pixel>>;

image imread(const std::string& path) {
    if (path.compare(path.size() - 4, 4, ".dat") != 0) {
        std::cerr << "Can read only prepared .dat files!" << std::endl;
        throw std::invalid_argument(path);
    }

    std::ifstream file(path, std::ios::binary | std::ios::in);

    std::uint32_t h, w, d;
    file.read(reinterpret_cast<char*>(&h), 4);
    file.read(reinterpret_cast<char*>(&w), 4);
    file.read(reinterpret_cast<char*>(&d), 4);

    auto data = std::vector<std::vector<pixel>>(h);
    for (auto& row: data) {
        row.resize(w);
    }

    for (int i = 0; i < h; ++i) {
        for (int j = 0; j < w; ++j) {
            auto pix = std::array<char, 3>();
            file.read(pix.data(), 3);
            data[i][j] = pixel { uint8_t(pix[0]),
                                 uint8_t(pix[1]),
                                 uint8_t(pix[2])};
        }
    }

    return data;
}

void imwrite(const image * source, const std::string& path, const frame * region) {
    int h = region->h; 
    int w = region->w; 
    int d = 3;
    std::ofstream file(path, std::ios::binary);
    file.write(reinterpret_cast<char*>(&h), 4);
    file.write(reinterpret_cast<char*>(&w), 4);
    file.write(reinterpret_cast<char*>(&d), 4);

    for (uint64_t x = region->x; x != region->x+region->h; ++x) {
        for (uint64_t y = region->y; y != region->y+region->w; ++y) {
            file.write(reinterpret_cast<const char*>(&(*source)[x][y].r), 1);
            file.write(reinterpret_cast<const char*>(&(*source)[x][y].g), 1);
            file.write(reinterpret_cast<const char*>(&(*source)[x][y].b), 1);
        }
    }
    file.close();
}

class cutter
{
	public:
		cutter(image const * pim, image  const * psmall)
		{
			image const im = *pim;
			image const small = *psmall;
			const uint32_t imH = im.size();
			const uint32_t imW = im[0].size();
			const uint32_t smallH = small.size();
			const uint32_t smallW = small[0].size();
			frameNumber = (imH - smallH)*(imW - smallW);
			frames = new frame[frameNumber];
			i = 0;
			for (int64_t x = 0; x < imH - smallH; ++x) 
			{
		        for (int64_t y = 0; y < imW - smallW; ++y, ++i) 
		        {
		            frames[i] = frame(x, y, smallW, smallH);
            	}
        	}
        	i = 0;
		}
		bool operator()(frame & result)
		{
			if (!frames) return false;
			if (i == frameNumber) 
			{
				delete[] frames;
				frames = nullptr;
				return false;
			}
			frame next = frames[i++];
			result = next;
			return true;
		}
		
	private:
		frame * frames;
		int64_t i, frameNumber;

};

inline int64_t difference(const pixel &l, const pixel &r) 
{
    return abs((int64_t) l.r - (int64_t) r.r) + abs((int64_t) l.b - (int64_t) r.b)
           + abs((int64_t) l.g - (int64_t) r.g);
}

class comparator 
{
	public:
		comparator(image const * im, image * const small) 
		: im(im), small(small)
		{}

		std::pair<frame, int64_t> operator()(const frame & region) 
		{
		    int64_t diff = 0;
		    for (int64_t x = 0; x < region.h; ++x) 
		    {
		        for (int64_t y = 0; y < region.w; ++y) 
		        {
		             diff += difference((*im)[region.x+x][region.y+y], (*small)[x][y]);
		        }
		    }
		    return std::make_pair(region, diff);
		}
    private:
    	image const * im;
    	image const * small;
};

class minimizer 
{
    public:
		minimizer(frame * res)
		: min(std::numeric_limits<int64_t>::max())
		, result(res)
		{}
		
    	frame operator()(std::pair<frame, int64_t> p) 
    	{
		    frame & region = p.first;
		    int64_t diff   = p.second;

		    if (diff < min) {
		        min = diff;
		        *result = frame(region);
		    }

		    return *result;
		}
    private:
    	int64_t min;
    	frame *result;
};

void find(const image * im, const std::string &path, const std::string &name) 
{
    image small = imread(path);
    frame *res = new frame(0,0,0,0);
    tbb::flow::graph g;
    
    // nodes
    source_node<frame> cuttingNode(g, cutter(im, &small), false);
	buffer_node<frame> frameBuffer(g);
    function_node<frame, std::pair<frame, int64_t>> comparingNode(g, unlimited, comparator(im, &small));
    buffer_node<std::pair<frame, int64_t>> compareBuffer(g);
    function_node<std::pair<frame, int64_t>> minimizingNode(g, 1, minimizer(res));

    // edges
    make_edge(cuttingNode,   frameBuffer);
    make_edge(frameBuffer,   comparingNode);
    make_edge(comparingNode, compareBuffer);
    make_edge(compareBuffer, minimizingNode);

    // work
    cuttingNode.activate();
    g.wait_for_all();

	// output
	std::cout << name << " " << res->y << " " << res->x << "\n";
    imwrite(im, name+"_res.dat", res);
 	delete res;
}

int main() 
{
	image const im = imread("../data/image.dat");
	
	find(&im, "../data/hat.dat", "hat");
	find(&im, "../data/chicken.dat", "chicken");
    find(&im, "../data/cheer.dat", "cheer");
    return 0;
}

