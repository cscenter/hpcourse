#define NO_COPY

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


#ifdef NO_COPY
	using subimage = std::vector<const pixel*>;
#else
	using subimage = image;
#endif

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
	for (auto& row : data) {
		row.resize(w);
	}

	for (size_t i = 0; i < h; ++i) {
		for (size_t j = 0; j < w; ++j) {
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
	int h = (int)source.size();
	int w = (int)source[0].size();
	int d = 3;
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

subimage Cut(const image& source, int x, int y, int height, int width) {
	subimage result;
	result.reserve(height);

	for (int i = 0; i < height; ++i)
	{
#ifdef NO_COPY
		result.emplace_back(&source[x + i][y]);
#else
		result.emplace_back(std::vector<pixel>(width));
		auto it = source[x + i].begin();
		std::copy(it + y, it + y + width, result.back().begin());
#endif
	}
	return result;
}

struct Coord
{
	int X;
	int Y;
	Coord() = default;
	Coord(int x, int y)	: X(x) , Y(y) {}
};

struct Size
{
	int Height;
	int Width;

	explicit Size(const image& value)
	{
		Height = value.size();
		Width = value[0].size();
	}
};


struct reader 
{
	reader(const image& smallImage, const image& bigImage)
		: m_smallImage(smallImage)
		, m_bigImageSize(bigImage)
		, m_smallImageSize(smallImage)
		, m_x(0)
		, m_y(0)
	{
		m_maxX = m_bigImageSize.Height - m_smallImageSize.Height;
		m_maxY = m_bigImageSize.Width - m_smallImageSize.Width;
	}

	bool operator()(Coord& result)	{
		if (m_y > m_maxY)
		{
			m_y = 0;
			m_x++;
		}

		if (m_x > m_maxX)
		{
			return false;
		}

		result = Coord(m_x, m_y);
		
		m_y++;

		return true;
	}

	const image& m_smallImage;
	Size m_bigImageSize;
	Size m_smallImageSize;
	int m_x;
	int m_y;
	int m_maxX;
	int m_maxY;
};

struct cutter 
{
	explicit cutter(const image& bigImage, const image& smallImage)
		: m_bigImage(bigImage)
		, m_smallImage(smallImage)
	{
		
	}

	std::tuple<subimage, Coord> operator()(Coord coord) const	{
		Size size(m_smallImage);
		auto cutted = Cut(m_bigImage, coord.X, coord.Y, size.Height, size.Width);
		return std::make_tuple(cutted, coord);
	}

	const image& m_bigImage;
	const image& m_smallImage;
};

class differ 
{
public:
	explicit differ(const image& smallImage)
		: m_smallImage(smallImage)
	{}

	std::tuple<Coord, int> operator()(std::tuple<subimage, Coord> v) const	{
		auto subimage = std::get<0>(v);
		auto coord = std::get<1>(v);
		int res = 0;
		for (int x = 0; x < m_smallImage.size(); ++x)
		{
			for (int y = 0; y < m_smallImage[0].size(); ++y)
			{
				res += abs(m_smallImage[x][y].b - subimage[x][y].b)
					 + abs(m_smallImage[x][y].g - subimage[x][y].g)
					 + abs(m_smallImage[x][y].r - subimage[x][y].r);
			}
		}
		return std::make_tuple(coord, res);
	}

private:
	const image& m_smallImage;
};

class minimizer {
	Coord& result;
	int currentMin = INT_MAX;
public:
	explicit minimizer(Coord& result) 
		: result(result)
	{
	}

	std::tuple<Coord, int> operator()(std::tuple<Coord, int> v)	{
		if (currentMin > std::get<1>(v))
		{
			currentMin = std::get<1>(v);
			result = std::get<0>(v);
		}

		return std::make_tuple(result, currentMin);
	}
};

void FindSubpicture(std::string bigImagePath, std::string subImagePath) {
	auto smallImage = imread(subImagePath);
	auto bigImage = imread(bigImagePath);

	graph g;

	source_node<Coord> input(g, reader(smallImage, bigImage), true);

	function_node<Coord, std::tuple<subimage, Coord>>
		cutterNode(g, unlimited, cutter(bigImage, smallImage));

	buffer_node<std::tuple<subimage, Coord>> cutterBuffer(g);

	function_node<std::tuple<subimage, Coord>, std::tuple<Coord, int>>
		diffNode(g, unlimited, differ(smallImage));

	Coord result;
	function_node<std::tuple<Coord, int>, std::tuple<Coord, int>>
		minimizerNode(g, serial, minimizer(result));

	make_edge(input, cutterNode);
	make_edge(cutterNode, cutterBuffer);
	make_edge(cutterBuffer, diffNode);
	make_edge(diffNode, minimizerNode);

	g.wait_for_all();

	std::cout << subImagePath << ": (" << result.X << "; " << result.Y << ")\n";
}

int main() {
	FindSubpicture("image.dat", "cheer.dat");
	FindSubpicture("image.dat", "chicken.dat");
	FindSubpicture("image.dat", "hat.dat");
	return 0;
}
