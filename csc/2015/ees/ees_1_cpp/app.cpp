#include "thread_pool.h"
#include "log.h"

#include <boost/program_options.hpp>

#include <stdexcept>
#include <map>
#include <vector>
#include <iostream>
#include <string>
#include <boost/algorithm/string.hpp>

size_t next_id()
{
    static size_t current_id = 0;
    return current_id++;
}

std::ostream& operator<<(std::ostream& stream, const TaskState& state)
{
    static std::map<TaskState, std::string> states {
        {TaskState::IDLE, "IDLE"},
        {TaskState::RUNNING, "RUNNING"},
        {TaskState::COMPLETE, "COMPLETE"},
        {TaskState::CANCELLED, "CANCELLED"}
    };

    stream << states[state];
    return stream;
}

class invalid_command_exception : public std::exception
{
    using std::exception::exception;
};

class invalid_task_id_exception : public std::exception
{
    using std::exception::exception;
};

std::vector<future<void>> tasks;
std::vector<std::string> commands {"create", "cancel", "status", "commands", "quit"};
std::vector<std::string> params   {"<seconds>", "<id>", "<id>", "", ""};

void handle_cancel(thread_pool& pool, size_t n)
{
    if (n >= tasks.size())
        throw invalid_task_id_exception();
    tasks[n].cancel();
}

void handle_create(thread_pool& pool, size_t n)
{
    static auto wait_task = [](size_t time) {
      boost::this_thread::sleep_for(boost::chrono::seconds(time));
    };
    tasks.push_back(pool.submit(wait_task, n));
    log::cout("Task id: ", tasks.size() - 1);
}

void handle_status(thread_pool&, size_t n)
{
    if (n >= tasks.size())
        throw invalid_task_id_exception();
    log::cerr(tasks[n].status());
}

void handle_commands()
{
    for (size_t i = 0; i < commands.size(); ++i)
    {
        log::cout(std::to_string(i) + ")", commands[i], params[i]);
    }
}

std::vector<std::function<void(thread_pool&, size_t)>> handlers {
                                   handle_create, handle_cancel, handle_status
                               };

std::map<std::string, std::function<void(thread_pool&, size_t)>> get_handlers()
{
    std::map<std::string, std::function<void(thread_pool&, size_t)>> result;
    for (size_t i = 0; i < handlers.size(); ++i)
    {
        result[commands[i]] = handlers[i];
    }
    return result;
}

void handle_command(thread_pool& pool, const std::string& command, size_t n)
{
    static auto commands_handlers = get_handlers();
    if (std::find(commands.begin(), commands.end(), command) == commands.end())
    {
        throw invalid_command_exception();
    }

    commands_handlers[command](pool, n);
}

void unknown_command()
{
    log::cout("Invalid command! here is what you can do:");
    handle_commands();
}

int main(int argc, char* argv[])
{
    namespace po = boost::program_options;
    po::options_description desc("Allowed options");
    desc.add_options()
        ("help", "produce help message")
        ("N", po::value<int>(), "pool thread count");

    po::variables_map vm;
    po::store(po::parse_command_line(argc, argv, desc), vm);
    po::notify(vm);

    if (!vm.count("N")) {
        log::cout(desc);
        return 1;
    }

    int N = vm["N"].as<int>();
    thread_pool pool(N);

    handle_commands();

    std::string command;
    std::vector<std::string> parts;
    while (true)
    {
        std::cout << "$ ";
        std::getline(std::cin, command);
        boost::algorithm::trim(command);
        boost::algorithm::split(parts, command, boost::algorithm::is_any_of("\t "), boost::algorithm::token_compress_on);

        if (parts.size() == 1)
        {
            if (command.empty())
            {
                continue;
            }
            if (command == "quit")
            {
                break;
            }
            else if (command == "commands")
            {
                handle_commands();
            }
            else
            {
                unknown_command();
            }
        }
        else
        {
            try
            {
                size_t n = std::stoul(parts[1]);
                handle_command(pool, parts[0], n);
            }
            catch (const invalid_task_id_exception&)
            {
              log::cerr("No such id in task queue!");
            }
            catch (const std::invalid_argument&)
            {
                unknown_command();
            }
            catch (const invalid_command_exception&)
            {
                unknown_command();
            }
        }
    }

    return 0;
}
