#include <iostream>
#include <string>

#include "thread_pool.hpp"
#include "command_line_parser/command_line_parser.hpp"

void task_one(int id) {
    std::cout << id << " function" << std::endl;
}

void task(int id, int N) {
    std::this_thread::sleep_for(std::chrono::milliseconds(N));
    std::cout << id << " function" << std::endl;
}

int main(int argc, char **argv)
{
    CommandLineParser parser;
    if (!parser.parse(argc, argv)) {
        return 1;
    }

    std_utils::thread_pool p(parser.count_threads);

    std::future<void> qw = p.push(std::move(task_one));
    p.push(task_one);
    p.push(task, 7);

    auto output_flag = true;
    auto command = 0;
    auto N = 0;
    std::string menu("");

    while (output_flag) {
        std::cin >> command;
        switch (command) {
        case 0:
            output_flag = false;
            break;
        case 1:
            break;
        case 2:
            std::cin >> N;
            p.push(task, N);
            break;
        case 3:
            std::cin >> N;
            std::cout << p.get_id(N) << std::endl; 
            break;
        default:
            break;
        }
    }

    return 0;
}
