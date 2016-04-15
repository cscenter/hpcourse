#include "server.hpp"

int main(int argc, char* argv[]) {

    if (argc != 2) {
        std::cerr << "Usage: blocking_tcp_echo_server <port>\n";
        return 1;
    }

    server sv(std::atoi(argv[1]));

    return 0;
}





















//
//
//condition_variable g_cond_var;
//mutex g_print_mutex;
//mutex g_task_mutex;
//bool g_notified = false;
//int64_t g_res;
//
//vector<int64_t> g_task_results;
//vector<bool> g_task_done;
//vector<thread> task_threads;
//vector<thread> subscribe_threads;
//
//void print(string s) {
//    mutex print_mutex;
//
//    unique_lock<mutex> print_lock(print_mutex);
//    //cout <<
//
//
//}
//int64_t task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n, int64_t ind) {
//
//    {
//        unique_lock<mutex> print_lock(g_print_mutex);
//        cout << "IND: " << ind << " TASK THREAD: " << this_thread::get_id() << endl;
//    }
//
//    while (n-- > 0) {
//        b = (a * p + b) % m;
//        a = b;
//    }
//    //this_thread::sleep_for(chrono::seconds(1 + rand()%5));
//    g_task_results[ind] = a;
//    {
//        unique_lock<mutex> task_lock(g_task_mutex);
//        g_task_done[ind] = true;
//        g_cond_var.notify_one();
//    }
//    {
//        unique_lock<mutex> print_lock(g_print_mutex);
//        cout << "*** LEAVING TASK THREAD " << ind << " WITH " << g_task_results[ind] << endl;
//    }
//    return a;
//}
//
//void subscribe(int64_t ind) {
//
//    {
//        unique_lock<mutex> print_lock(g_print_mutex);
//        cout << "SUBSCRIBE THREAD: " << this_thread::get_id() << endl;
//    }
//
//
//    unique_lock<mutex> wait_lock(g_task_mutex);
//    while (!g_task_done[ind]) {
//        {
//            unique_lock<mutex> print_lock(g_print_mutex);
//            cout << "WAITING RESULT FOR: " << ind << endl;
//        }
//        g_cond_var.wait(wait_lock);
//    }
//    // обрабатываем результат
//    {
//        unique_lock<mutex> print_lock(g_print_mutex);
//        cout << "RESULT: " << g_task_results[ind] << endl;
//        cout << "LEAVING SUBSCRIBE THREAD: " << endl;
//    }
//}
//
//void list_cur_tasks() {
//
//    //this_thread::sleep_for(chrono::milliseconds(7000));
//    {
//        unique_lock<mutex> print_lock(g_print_mutex);
//        cout << "LIST THREAD: " << this_thread::get_id() << endl;
//        cout << "CURRENT TASKS ARE WORKING: " << endl;
//
//        for (int i = 0; i < task_threads.size(); ++i) {
//
//            if (!g_task_done[i]) {
//                cout << i << ' ' << task_threads[i].get_id() << endl;
//            }
//        }
//
//        cout << "LEAVING LIST THREAD: " << endl;
//    }
//}
//
//
//int main() {
//
//    int n = 5;
//    int task_number = 0;
//
//    for (int i = 0; i < n; ++i) {
//
//        size_t type_;
//        int64_t ind_;
//
//        cin >> type_;
//
//        if (type_ == 1) {
//            task_threads.push_back(thread(task, rand(), rand(), rand(), rand(), rand(), task_number));
//            g_task_done.push_back(false);
//            g_task_results.push_back(-1);
//            task_number++;
//        }
//        if(type_ == 2) {
//
//            cin >> ind_;
//            subscribe_threads.push_back(thread(subscribe, ind_));
//        }
//        if (type_ == 3) {
//
//            thread list_thr(list_cur_tasks);
//            list_thr.join();
//        }
//
//    }
//
//    for (int i = 0; i < task_threads.size(); ++i) {
//        task_threads[i].join();
//    }
//    for (int i = 0; i < subscribe_threads.size(); ++i) {
//        subscribe_threads[i].join();
//    }
//
//
//    cout << "RESULTS: " << endl;
//    for (int i = 0; i < g_task_results.size(); ++i) {
//        cout << i << ": " << g_task_results[i] << endl;
//    }
//
//
//
//
//    return 0;
//}