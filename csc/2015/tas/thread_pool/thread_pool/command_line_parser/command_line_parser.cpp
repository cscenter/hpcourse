#include "command_line_parser.hpp"

#include <boost/program_options.hpp>
#include <iostream>

namespace po = boost::program_options;

bool CommandLineParser::parse(int argc, char *argv[])
{
    po::options_description desc("Allowed options");
    desc.add_options()
        ("help", "produce help message")
        ("count,c", po::value<int>(&count_threads), "count threads")
        ;

    try {
        po::variables_map vm;
        po::store(po::parse_command_line(argc, argv, desc), vm);
        po::notify(vm);

        if (vm.count("help") || (argc == 1)) {
            std::cout << desc << std::endl;
            return false;
        }

        if (!vm.count("count")) {
            std::cout << "Count threads was not set." << std::endl;
            return false;
        }
        
        if (count_threads <= 0) {
            std::cout << "Incorrectly count threads.";
            return false;
        }

    } catch (const boost::exception & e) {
        std::cout << "Incorrectly command line format.";
        return false;
    }

    return true;
}
