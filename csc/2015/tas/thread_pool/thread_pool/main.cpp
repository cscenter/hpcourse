#include <string>
#include <iostream>

#include "thread_pool.hpp"
#include "command_line_parser.hpp"

void task_one(int id) {
    std::cout << id << " function\n";
}

void task_two(int id, int N) {
    std::this_thread::sleep_for(std::chrono::milliseconds(N));
    std::cout << id << " function" << std::endl;
}

int main(int argc, char **argv)
{
    std::ios_base::sync_with_stdio(false);

    CommandLineParser parser;
    if (!parser.parse(argc, argv)) {
        return 1;
    }

    std_utils::thread_pool p(parser.count_threads);

    auto qw = p.submit(std::move(task_one));

    auto output_flag = true;
    auto command = 0;
    auto N = 0;
    std::string menu("0 - Exit\n"
        "1 - To add a task with a duration of N seconds\n"
        "2 - To remove a task from execution by its ID\n"
        "3 - To request the status of the task\n"
        "Your choice: "
        );

    while (output_flag) {
        std::cout << menu;
        std::cin >> command;
        switch (command) {
        case 0:
            output_flag = false;
            break;
        case 1:
            std::cin >> N;
            p.submit(task_two, 1000 * N);
            break;
        case 2:
            std::cin >> N;
            p.interrupt(N);
            break;
        case 3:
            std::cin >> N;
            std::cout << p.get_id(N) << std::endl; 
            break;
        default:
            std::cout << "Error\n\n";
            break;
        }
    }

    return 0;
}
