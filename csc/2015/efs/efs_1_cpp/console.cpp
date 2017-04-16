#include <iostream>
#include <chrono>
#include <cstdio>
#include <sstream>
#include "ThreadPool.h"

using namespace std;

void help() {
    cout << "Commands available:\n"
    "add <length> - adds new task which will terminate after <length> seconds, prints task's id\n"
    "interrupt <id> - interrupts task with corresponding id\n"
    "query <id> - requests information about corresponding task\n"
    "list - lists information about all tasks\n"
    "help - prints this message\n"
    "quit - interrupts all tasks and terminates the application\n";
}

int worker(Task &task, int length) {
    auto terminateAt = std::chrono::steady_clock::now() + std::chrono::seconds(length);
    unsigned counter = 0, sum = 0;
    for (; std::chrono::steady_clock::now() < terminateAt; counter++) {
        sum += counter;
        if (task.interrupted()) {
            throw counter;
        }
    }
    return counter;
}

void info(int id, PTask task) {
    TaskState s = task->state();
    const char *names[] = { "QUEUED", "RUNNING", "EXCEPTION_THROWN", "FINISHED", "CANCELLED" };
    cout << "Task #" << id << ": " << names[(int)s] << "\n";
    if (s == TaskState::FINISHED) {
        TaskResult result = task->wait();
        cout << "  Returned " << result.returned << "\n";
    }
    if (s == TaskState::EXCEPTION_THROWN) {
        TaskResult result = task->wait();
        try {
            rethrow_exception(result.exception);
        } catch (unsigned counter) {
            cout << "  unsigned " << counter << "\n";
        } catch (...) {
            cout << "  unknown exception\n";
        }
    }
}

int main(int argc, char* argv[]) {
    int count;
    if (argc != 2 || sscanf(argv[1], "%d", &count) != 1 || count < 1) {
        cerr << "Usage: " << argv[0] << " <number of threads>\n";
        return 1;
    }

    ThreadPool pool(count);
    std::vector<PTask> tasks;
    for (;;) {
        cout << "> ";
        string line;
        getline(cin, line);
        if (line.empty()) {
            help();
            continue;
        }

        stringstream reader;
        reader << line;
        reader.exceptions(ios_base::badbit | std::ios_base::failbit);

        try {
            string command;
            reader >> command;

            if (command == "add") {
                int length;
                reader >> length;
                int id = tasks.size() + 1;
                tasks.push_back(pool.submit([id, length](Task &task) { return worker(task, length); }));
                printf("Task %d was created, duration = %ds\n", id, length);
            } else if (command == "interrupt" || command == "query") {
                int id;
                reader >> id;
                if (id < 1 || id > (int)tasks.size()) {
                    cout << "There are only " << tasks.size() << " threads, please specify and id from 1 to " << tasks.size() << "\n";
                } else {
                    PTask t = tasks[id - 1];
                    if (command == "interrupt") {
                        t->interrupt();
                    } else {
                        info(id, t);
                    }
                }
            } else if (command == "list") {
                for (size_t i = 0; i < tasks.size(); i++) {
                    info(i + 1, tasks[i]);
                }
            } else if (command == "help") {
                help();
            } else if (command == "quit") {
                for (auto x : tasks)
                    x->interrupt();
                break;
            } else {
                cout << "Unknown command: '" << command << "'. Type 'help' to get help.\n";
            }
        } catch (ios_base::failure) {
            cout << "Invalid command. Type 'help' to get help.\n";
        }
    }
    cout << "Quitting...\n";
    return 0;
}
