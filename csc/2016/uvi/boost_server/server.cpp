#include <thread>
#include <iostream>
#include "server.hpp"
#include "data_handler.hpp"

using namespace std;
server::server() {}

void server::server_impl(unsigned short port)
{
    boost::asio::io_service io_service;

    tcp::acceptor a(io_service, tcp::endpoint(tcp::v4(), port));
    for (;;)
    {
        tcp::socket socket(io_service);
        a.accept(socket);
        std::thread(&server::session, this, std::move(socket)).detach();
    }
}

void server::session(tcp::socket socket)
{
    try
    {
        int const max_len = 1024;
        task_manager manager;

        for (;;)
        {
            char data_req[max_len];
            memset(data_req, 0, sizeof data_req);

            boost::system::error_code error;
            size_t read_len = socket.read_some(boost::asio::buffer(data_req), error);

            // Connection closed cleanly by peer.
            if (error == boost::asio::error::eof) { break; }
            // Some other error.
            else if (error) { throw boost::system::system_error(error); }

            data_handler handler;
            auto msg_req = handler.parse(data_req, read_len);

            if (msg_req.has_request())
            {
                auto msg_resp = build_response(msg_req,
                                               manager,
                                               msg_req.request().client_id(),
                                               msg_req.request().request_id());

                char data_resp[max_len];
                int len = handler.serialize(msg_resp, data_resp, max_len);

                boost::asio::write(socket, boost::asio::buffer(data_resp, len));
            }
        }
    }
    catch (std::exception& e)
    {
        std::cerr << "Exception in thread: " << e.what() << "\n";
    }
}

communication::WrapperMessage server::
build_response(communication::WrapperMessage &msg_req,
               task_manager& manager,
               std::string const& client_id,
               int64_t request_id)
{
    communication::WrapperMessage msg_resp;
    msg_resp.mutable_response()->set_request_id(request_id);

    if (msg_req.request().has_submit())
    {
        auto task_args = msg_req.request().submit().task();
        int64_t n = msg_req.request().submit().task().n();

        // возвращает SubmitTaskResponse
        *(msg_resp.mutable_response()->mutable_submitresponse()) = manager.submit_task(client_id, request_id, task_args, n);
    }

    else if (msg_req.request().has_subscribe())
    {
        int64_t task_id = msg_req.request().subscribe().taskid();

        // возвращает SubscribeResponse
        *(msg_resp.mutable_response()->mutable_subscriberesponse()) = manager.subscribe(task_id);
    }
        
    else if (msg_req.request().has_list())
    {
        // возвращает ListResponse
        *(msg_resp.mutable_response()->mutable_listresponse()) = manager.list_tasks();;
    }
    return msg_resp;
}
