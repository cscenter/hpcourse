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
    //int const max_len = 1024;
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

//            if (msg_req.has_request())
//            {
//                cout << endl << "Built message to server has request. Req_type = " << req_type << endl;
//                if (msg_req.request().has_submit()) { cout << "\tsubmit request" << endl; }
//                else if (msg_req.request().has_subscribe()) { cout << "\tsubscribe request" << endl; }
//                else if (msg_req.request().has_list()) { cout << "\tlist request" << endl; }
//            }

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
        cout << "Submit task request." << endl;

        auto a = std::make_pair(rand(), 0);
        auto b = std::make_pair(rand(), 0);
        auto p = std::make_pair(rand(), 0);
        auto m = std::make_pair(rand(), 0);
//        int64_t n = rand();
//        int64_t n = 10000000000;

//        auto a = std::make_pair(1000, 0);
//        auto b = std::make_pair(2000, 0);
//        auto p = std::make_pair(3000, 0);
//        auto m = std::make_pair(4000, 0);
        int64_t n = 1000000000;






//
//
//            auto a = input_submit_task_param('a');
//            auto b = input_submit_task_param('b');
//            auto p = input_submit_task_param('p');
//            auto m = input_submit_task_param('m');
//
//            int64_t n = -1; cout << "Enter n: "; cin >> n;
//
        //auto msg_req.mutable_request()->mutable_submit()->mutable_task() = msg_req.mutable_request()->mutable_submit()->mutable_task();

        if (a.second)
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_a()->set_dependenttaskid(a.first);
        }
        else
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_a()->set_value(a.first);
        }

        if (b.second)
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_b()->set_dependenttaskid(b.first);
        }
        else
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_b()->set_value(b.first);
        }

        if (p.second)
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_p()->set_dependenttaskid(p.first);
        }
        else
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_p()->set_value(p.first); }

        if (m.second)
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_m()->set_dependenttaskid(m.first);
        }
        else
        {
            msg_req.mutable_request()->mutable_submit()->mutable_task()->mutable_m()->set_value(m.first);
        }

        msg_req.mutable_request()->mutable_submit()->mutable_task()->set_n(n);
    }
        // subscribe
    else if (req_type == 2)
    {
        cout << "Subscribe task request." << endl;
        cout << "Enter task_id: ";
        int32_t task_id = -1; cin >> task_id;

        msg_req.mutable_request()->mutable_subscribe()->set_taskid(task_id);
    }
        // list
    else if (req_type == 3)
    {
        cout << "List request." << endl;
        // по идее устанавливает флажок has_list
        msg_req.mutable_request()->mutable_list();
    }

}
void client::send_resquest(tcp::socket& socket, communication::WrapperMessage const& msg_req)
{
    int const max_len = 1024;
    char data_req[max_len];
    // очищаем массив от старых данных
    memset(data_req, 0, sizeof data_req);

    data_handler handler;
    size_t len = handler.serialize(msg_req, data_req, max_len);

    boost::asio::write(socket, boost::asio::buffer(data_req, len));
}
void client::get_server_response(tcp::socket& socket)
{
    int const max_len = 1024;
    char data_resp[max_len];
    // очищаем массив от старых данных
    memset(data_resp, 0, sizeof data_resp);

    boost::system::error_code error;
    // клиент читает ответ
    size_t len = socket.read_some(boost::asio::buffer(data_resp), error);

    if (error)
    {
        throw boost::system::system_error(error); // Some other error.
    }
//    cout << "Response recieved. OK. " << endl;
//    std::cout << "response size: " << len << std::endl;

    data_handler handler;
    auto msg_resp = handler.parse(data_resp, len);

    if (msg_resp.has_request()) { cout << endl << "Message from server has request: " << endl; }
    if (msg_resp.has_response())
    {
        cout << endl << "Message from server has response" << endl;
        if (msg_resp.response().has_submitresponse()) { cout << "\tsubmit response" << endl; }
        else if (msg_resp.response().has_subscriberesponse()) { cout << "\tsubscribe response" << endl; }
        else if (msg_resp.response().has_listresponse()) { cout << "\tlist response" << endl; }
    }

    print_response(msg_resp);
}
void client::print_response(communication::WrapperMessage const& msg) const
{
    cout << "----------------------------------" << endl;
    //cout << "SERVER RESPONSE: " << endl;
    cout << "request_id: " << msg.response().request_id() << endl;

    if (msg.response().has_submitresponse())
    {
        //cout << "Submit task response. " << endl;
        cout << "Status: ";

        if (msg.response().submitresponse().status() == communication::Status::OK) { cout << "OK" << endl; }
        else { cout << "ERROR" << endl; }

        cout << "Submitted task_id: " << msg.response().submitresponse().submittedtaskid() << endl;
    }
    else if (msg.response().has_subscriberesponse())
    {
        cout << "Subscribe task response. " << endl;
        cout << "Status: ";

        if (msg.response().subscriberesponse().status() == communication::Status::OK) { cout << "OK" << endl; }
        else { cout << "ERROR" << endl; }

        if (msg.response().subscriberesponse().has_value())
        {
            cout << "Calculation result: " << msg.response().subscriberesponse().value() << endl;
        }
        else { cout << "Calculation is still in process..." << endl; }
    }
    else if (msg.response().has_listresponse())
    {
        cout << "List tasks response. " << endl;
        cout << "Status: ";

        if (msg.response().listresponse().status() == communication::Status::OK) { cout << "OK" << endl; }
        else { cout << "ERROR" << endl; }

        cout << "Tasks description: " << endl;
        int32_t tasks_size = msg.response().listresponse().tasks_size();
        cout << "Tasks size: " << tasks_size << endl;

        for (int i = 0; i < tasks_size; ++i)
        {
            auto list_resp = msg.response().listresponse().tasks(i);
            cout << "task_id: " << list_resp.taskid() << ' ';
            cout << "client_id: " << list_resp.clientid() << endl;
            cout << "a: " << list_resp.task().a().value() << ", ";
            cout << "b: " << list_resp.task().b().value() << ", ";
            cout << "p: " << list_resp.task().p().value() << ", ";
            cout << "m: " << list_resp.task().m().value() << ", ";
            cout << "n: " << list_resp.task().n() << ". " << endl;
            if (list_resp.has_result())
            {
                cout << "result: " << list_resp.result() << endl << endl;
            }
        }
    }
    cout << "----------------------------------" << endl;
}
