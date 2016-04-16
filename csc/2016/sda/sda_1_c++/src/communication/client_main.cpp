#include "Client.hpp"

using namespace boost::asio;
using namespace communication;

int main(int argc, char* argv[]) {

    io_service service;
    ip::tcp::endpoint ep(ip::address::from_string("127.0.0.1"), 8001);

    Task *task = new Task();
    Task_Param *param_a = new Task_Param();
    param_a->set_value(int64_t(1));
    task->set_allocated_a(param_a);

    Task_Param *param_b = new Task_Param();
    param_b->set_value(int64_t(4));
    task->set_allocated_b(param_b);

    Task_Param *param_p = new Task_Param();
    param_p->set_value(int64_t(11));
    task->set_allocated_p(param_p);

    Task_Param *param_m = new Task_Param();
    param_m->set_value(int64_t(22));
    task->set_allocated_m(param_m);
    task->set_n(10);

    SubmitTask *s_task = new SubmitTask();
    s_task->set_allocated_task(task);
    ServerRequest *req = new ServerRequest();
    req->set_allocated_submit(s_task);
    req->set_client_id("TEST");
    req->set_request_id(1987);

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
