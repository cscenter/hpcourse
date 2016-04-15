#include "client.hpp"
#include <iostream>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>

using std::cout;
using std::cin;
using std::endl;

client::client() {}

void client::run_request_session(std::string host, std::string port) {

    try {
        boost::asio::io_service io_service;
        tcp::socket sock(io_service);
        tcp::resolver resolver(io_service);
        boost::asio::connect(sock, resolver.resolve({host, port}));

        request(sock);
    }
    catch (std::exception& e) { std::cerr << "Exception: " << e.what() << endl; }
}

void client::request(tcp::socket& sock) {
    std::string client_id;
    int64_t request_id = 0;
    cout << "Enter your client_id and request_id: "; cin >> client_id >> request_id;

    communication::WrapperMessage wrap_msg_request;
    auto mut_req_ptr = wrap_msg_request.mutable_request();

    mut_req_ptr->set_client_id(client_id);
    mut_req_ptr->set_request_id(request_id);

    int32_t request_type = 0;
    while (request_type != -1) {
        cout << "Enter request type: "; cin >> request_type;

        // submit task
        if (request_type == 1) {
            cout << "Submit task request." << endl;
            auto a = input_submit_task_param('a');
            auto b = input_submit_task_param('b');
            auto p = input_submit_task_param('p');
            auto m = input_submit_task_param('m');

            int64_t n = -1; cout << "Enter n: "; cin >> n;

            auto mut_task_ptr = mut_req_ptr->mutable_submit()->mutable_task();

            if (a.second) { mut_task_ptr->mutable_a()->set_dependenttaskid(a.first); }
            else { mut_task_ptr->mutable_a()->set_value(a.first); }

            if (b.second) { mut_task_ptr->mutable_b()->set_dependenttaskid(b.first); }
            else { mut_task_ptr->mutable_b()->set_value(b.first); }

            if (p.second) { mut_task_ptr->mutable_p()->set_dependenttaskid(p.first); }
            else { mut_task_ptr->mutable_p()->set_value(p.first); }

            if (m.second) { mut_task_ptr->mutable_m()->set_dependenttaskid(m.first); }
            else { mut_task_ptr->mutable_m()->set_value(m.first); }

            mut_task_ptr->set_n(n);
        }
            // subscribe
        else if (request_type == 2) {

            int32_t task_id = -1;
            cout << "Subscribe request." << endl << "Enter task_id: "; cin >> task_id;

            mut_req_ptr->mutable_subscribe()->set_taskid(task_id);
        }
            // list
        else if (request_type == 3) {
            cout << "List request." << endl;
            // по идее устанавливает флажок has_list
            mut_req_ptr->mutable_list();
        }

        send_request(sock, wrap_msg_request);
        get_response(sock);
    }
}

std::pair<int64_t, int32_t> client::input_submit_task_param(char param) {

    int64_t parameter = -1;
    bool parameter_type = -1;

    cout << "Enter parameter \"" << param << "\" (value or id of another task): ";
    cin >> parameter;
    cout << "Is \"" << param << "\" a dependent parameter? (0 - NO | 1 - YES)";
    cin >> parameter_type;

    return std::make_pair(parameter, parameter_type);
}

void client::send_request(tcp::socket& sock, communication::WrapperMessage const &msg) {

    cout << "Sending your request..." << endl;
    const int32_t max_length = 1024;
    uint8_t data[max_length];

    google::protobuf::io::ArrayOutputStream aos(data, max_length);
    google::protobuf::io::CodedOutputStream cos(&aos);

    cos.WriteVarint32(msg.ByteSize());
    msg.SerializeToArray(data, msg.ByteSize());

    boost::asio::write(sock, boost::asio::buffer(data, msg.ByteSize()));
}

void client::get_response(tcp::socket& sock) {

    const int32_t max_length = 1024;
    char reply_data[max_length];

    size_t reply_length = boost::asio::read(sock, boost::asio::buffer(reply_data, max_length));


    google::protobuf::io::ArrayInputStream ais(reply_data, reply_length);
    google::protobuf::io::CodedInputStream cis(&ais);

    uint32_t msg_length = 0;
    cis.ReadVarint32(&msg_length);

    auto limit = cis.PushLimit(msg_length);

    communication::WrapperMessage msg_response;
    msg_response.ParseFromCodedStream(&cis);

    cis.PopLimit(limit);

    print_response(msg_response);
}

void client::print_response(communication::WrapperMessage const &msg) {

    cout << "Server response: " << endl;
    cout << "request_id: " << msg.response().request_id() << endl;

    if (msg.response().has_submitresponse()) {
        cout << "Submit task response. " << endl;

        cout << "Status: ";
        if (msg.response().submitresponse().status() == communication::Status::OK) { cout << "OK" << endl; }
        else { cout << "ERROR" << endl; }

        cout << "Submitted task_id: " << msg.response().submitresponse().submittedtaskid() << endl;

    }
    else if (msg.response().has_subscriberesponse()) {
        cout << "Subscribe task response. " << endl;
        cout << "Status: ";
        if (msg.response().subscriberesponse().status() == communication::Status::OK) { cout << "OK" << endl; }
        else { cout << "ERROR" << endl; }

        if (msg.response().subscriberesponse().has_value()) {
            cout << "Calculation result: " << msg.response().subscriberesponse().value() << endl;
        }
        else { cout << "Calculation is still in process..." << endl; }
    }
    else if (msg.response().has_listresponse()) {
        cout << "List tasks response. " << endl;
        cout << "Status: ";
        if (msg.response().listresponse().status() == communication::Status::OK) { cout << "OK" << endl; }
        else { cout << "ERROR" << endl; }

        cout << "Tasks description: " << endl;
        int32_t tasks_size = msg.response().listresponse().tasks_size();
        for (int i = 0; i < tasks_size; ++i) {

            auto list_resp = msg.response().listresponse().tasks(i);
            cout << "task_id: " << list_resp.taskid() << ' ';
            cout << "client_id: " << list_resp.clientid() << endl;
            cout << "a: " << list_resp.task().a().value() << ", ";
            cout << "b: " << list_resp.task().b().value() << ", ";
            cout << "p: " << list_resp.task().p().value() << ", ";
            cout << "m: " << list_resp.task().m().value() << ", ";
            cout << "n: " << list_resp.task().n() << ". " << endl;
            if (list_resp.has_result()) {
                cout << "result: " << list_resp.result() << endl << endl;
            }
        }
    }
}

