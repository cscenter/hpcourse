#include "Client.hpp"
#include <cstdlib>
#include <ctime>

using namespace boost::asio;
using namespace communication;

std::vector<boost::shared_ptr<boost::thread>> threads;

void start_task() {
    io_service service;
    ip::tcp::endpoint ep(ip::address::from_string("127.0.0.1"), 8001);

    Task *task = new Task();
    Task_Param *param_a = new Task_Param();
    param_a->set_value(int64_t(std::rand() % 34000));
    task->set_allocated_a(param_a);

    Task_Param *param_b = new Task_Param();
    param_b->set_value(int64_t(std::rand() % 34000));
    task->set_allocated_b(param_b);

    Task_Param *param_p = new Task_Param();
    param_p->set_value(int64_t(std::rand() % 34000));
    task->set_allocated_p(param_p);

    Task_Param *param_m = new Task_Param();
    param_m->set_value(int64_t(std::rand() % 34000));
    task->set_allocated_m(param_m);
    task->set_n(std::rand() % 1000);

    SubmitTask *s_task = new SubmitTask();
    s_task->set_allocated_task(task);
    ServerRequest *req = new ServerRequest();
    req->set_allocated_submit(s_task);
    req->set_client_id("TEST");
    req->set_request_id(std::rand() % 100);

    WrapperMessage msg;
    msg.set_allocated_request(req);

    Client::ptr cl = Client::get_new(service);
    std::string output;
    if ( msg.SerializeToString(&output) ) {

        cl->set_message(output);
        cl->start(ep);
    }
    service.run();
}

void start_subs() {
    io_service service;
    ip::tcp::endpoint ep(ip::address::from_string("127.0.0.1"), 8001);

    Subscribe *subscribe = new Subscribe();
    subscribe->set_taskid(3);
    ServerRequest *req = new ServerRequest();
    req->set_allocated_subscribe(subscribe);
    // req->set_client_id("TEST");
    req->set_request_id(std::rand() % 100);

    WrapperMessage msg;
    msg.set_allocated_request(req);

    Client::ptr cl = Client::get_new(service);
    std::string output;
    if ( msg.SerializeToString(&output) ) {

        cl->set_message(output);
        cl->start(ep);
    }
    service.run();
}

void start_get_list() {
    io_service service;
    ip::tcp::endpoint ep(ip::address::from_string("127.0.0.1"), 8001);

    ListTasks *list = new ListTasks();
    ServerRequest *req = new ServerRequest();
    req->set_client_id("TEST");
    req->set_allocated_list(list);
    req->set_request_id(std::rand() % 100);

    WrapperMessage msg;
    msg.set_allocated_request(req);

    Client::ptr cl = Client::get_new(service);
    std::string output;
    if ( msg.SerializeToString(&output) ) {

        cl->set_message(output);
        cl->start(ep);
    }
    service.run();
}

int main(int argc, char* argv[]) {
    std::srand(unsigned(std::time(0)));

    for (int i = 0; i < 10; ++i) {
        boost::shared_ptr<boost::thread> c = boost::shared_ptr<boost::thread>(new boost::thread(start_task));
        threads.push_back(c);
    }

    boost::shared_ptr<boost::thread> c = boost::shared_ptr<boost::thread>(new boost::thread(start_get_list));
    threads.push_back(c);

    for (int i = 0; i < 11; ++i) {
        threads[i]->join();
    }
}
