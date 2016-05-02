#include "client.hpp"
#include <iostream>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
#include <vector>
#include "../boost_server/data_handler.hpp"

using std::cout;
using std::cin;
using std::endl;
using std::string;

client::client() {}

void client::client_impl(std::string host, std::string port)
{
    try
    {
        boost::asio::io_service io_service;

        tcp::socket socket(io_service);
        tcp::resolver resolver(io_service);
        boost::asio::connect(socket, resolver.resolve({host, port}));


        cout << "Enter client_id and request_id: ";
        std::string client_id;
        int64_t request_id = 0;
        cin >> client_id >> request_id;

        communication::WrapperMessage msg_req;

        msg_req.mutable_request()->set_client_id(client_id);
        msg_req.mutable_request()->set_request_id(request_id);

        for(;;)
        {
            msg_req.mutable_request()->clear_submit();
            msg_req.mutable_request()->clear_subscribe();
            msg_req.mutable_request()->clear_list();

            cout << "Enter request type: " << endl << "(1 - submit task, 2 - subscribe task, 3 - list tasks)" << endl;

            int req_type = 0; cin >> req_type; if (req_type == -1) { break; }

            build_request(msg_req, req_type);

            send_resquest(socket, msg_req);

            get_server_response(socket);
        }
    }
    catch (std::exception& e)
    {
        std::cerr << "Exception: " << e.what() << "\n";
    }
}

void client::build_request(communication::WrapperMessage &msg_req, int req_type) 
{
    // submit task
    if (req_type == 1)
    {
        auto a = std::make_pair(rand(), 0);
        auto b = std::make_pair(rand(), 0);
        auto p = std::make_pair(rand(), 0);
        auto m = std::make_pair(rand(), 0);
        int64_t n = 1000000000;

        auto mut_tsk = msg_req.mutable_request()->mutable_submit()->mutable_task();
        if (a.second)
        {
            mut_tsk->mutable_a()->set_dependenttaskid(a.first);
        }
        else
        {
            mut_tsk->mutable_a()->set_value(a.first);
        }

        if (b.second)
        {
            mut_tsk->mutable_b()->set_dependenttaskid(b.first);
        }
        else
        {
            mut_tsk->mutable_b()->set_value(b.first);
        }

        if (p.second)
        {
            mut_tsk->mutable_p()->set_dependenttaskid(p.first);
        }
        else
        {
            mut_tsk->mutable_p()->set_value(p.first); }

        if (m.second)
        {
            mut_tsk->mutable_m()->set_dependenttaskid(m.first);
        }
        else
        {
            mut_tsk->mutable_m()->set_value(m.first);
        }

        mut_tsk->set_n(n);
    }
        // subscribe
    else if (req_type == 2)
    {
        cout << "Enter task_id: ";
        int32_t task_id = -1; cin >> task_id;

        msg_req.mutable_request()->mutable_subscribe()->set_taskid(task_id);
    }
        // list
    else if (req_type == 3)
    {
        msg_req.mutable_request()->mutable_list();
    }

}
void client::send_resquest(tcp::socket& socket, communication::WrapperMessage const& msg_req)
{
    int const max_len = 1024;
    char data_req[max_len];
    memset(data_req, 0, sizeof data_req);

    data_handler handler;
    size_t len = handler.serialize(msg_req, data_req, max_len);

    boost::asio::write(socket, boost::asio::buffer(data_req, len));
}
void client::get_server_response(tcp::socket& socket)
{
    int const max_len = 1024;
    char data_resp[max_len];
    memset(data_resp, 0, sizeof data_resp);

    boost::system::error_code error;
    size_t len = socket.read_some(boost::asio::buffer(data_resp), error);

    if (error)
    {
        throw boost::system::system_error(error); // Some other error.
    }
//    cout << "Response recieved. OK. " << endl;
//    std::cout << "response size: " << len << std::endl;

    data_handler handler;
    auto msg_resp = handler.parse(data_resp, len);

    print_response(msg_resp);
}
void client::print_response(communication::WrapperMessage const& msg) const
{
    cout << "----------------------------------" << endl;
    cout << "request_id: " << msg.response().request_id() << endl;

    if (msg.response().has_submitresponse())
    {
        cout << "status: ";
        if (msg.response().submitresponse().status() == communication::Status::OK)
        {
            cout << "OK" << endl;
        }
        else
        {
            cout << "ERROR" << endl;
            return;
        }

        cout << "\tsubmitted task_id: " << msg.response().submitresponse().submittedtaskid() << endl << endl;
    }
    else if (msg.response().has_subscriberesponse())
    {
        cout << "status: ";
        if (msg.response().subscriberesponse().status() == communication::Status::OK)
        {
            cout << "OK" << endl;
        }
        else
        {
            cout << "ERROR" << endl;
            return;
        }

        if (msg.response().subscriberesponse().has_value())
        {
            cout << "calculation result: " << msg.response().subscriberesponse().value() << endl << endl;
        }
        else
        {
            cout << "calculation is still in progress..." << endl << endl;
        }
    }
    else if (msg.response().has_listresponse())
    {
        cout << "status: ";
        if (msg.response().listresponse().status() == communication::Status::OK)
        {
            cout << "OK" << endl;
        }
        else
        {
            cout << "ERROR" << endl;
            return;
        }

        cout << "tasks description: " << endl << endl;

        int32_t tasks_size = msg.response().listresponse().tasks_size();
        for (int i = 0; i < tasks_size; ++i)
        {
            auto list_resp = msg.response().listresponse().tasks(i);
            cout << "task_id: " << list_resp.taskid() << endl;
            cout << "client_id: " << list_resp.clientid() << endl;
            cout << "\ta: " << list_resp.task().a().value() << ", ";
            cout << "b: " << list_resp.task().b().value() << ", ";
            cout << "p: " << list_resp.task().p().value() << ", ";
            cout << "m: " << list_resp.task().m().value() << ", ";
            cout << "n: " << list_resp.task().n() << ". " << endl;

            if (list_resp.has_result())
            {
                cout << "\tresult: " << list_resp.result() << endl;
            }
            else
            {
                cout << "calculation is still in progress..." << endl << endl;
            }
            cout << endl;
        }
    }
    cout << "----------------------------------" << endl;
}
