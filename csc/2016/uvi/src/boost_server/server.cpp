#include <thread>
#include "server.hpp"
#include "data_handler.hpp"

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
            // очищаем массив от старых данных
            memset(data_req, 0, sizeof data_req);

            boost::system::error_code error;
            // сервер читает данные
            size_t len = socket.read_some(boost::asio::buffer(data_req), error);

            // Connection closed cleanly by peer.
            if (error == boost::asio::error::eof) { break; }
            else if (error)
            {
                throw boost::system::system_error(error); // Some other error.
            }

            std::cout << "size of accepted data: " << len << std::endl;
//            std::cout << "------------------------------------" << std::endl;
//            std::string tmp = data_req; std::cout << "SERVER SESSION. ACCEPTED DATA: " << tmp << std::endl;
//            std::cout << "------------------------------------" << std::endl;


            data_handler handler;
            auto msg_req = handler.parse(data_req, len);

            if (msg_req.has_request())
            {
                std::cout << "Message has request" << std::endl;
                auto msg_resp = build_response(msg_req, manager, msg_req.request().client_id(), msg_req.request().request_id());

                char data_resp[max_len];
                int len = handler.serialize(msg_resp, data_resp, max_len);

                // сервер посылает ответ
                boost::asio::write(socket, boost::asio::buffer(data_req, len));

            }

//            std::string tmp = data_req; std::cout << "SERVER SESSION. ACCEPTED DATA: " << tmp << std::endl;
//            boost::asio::write(socket, boost::asio::buffer(data_req, len));



        }
    }
    catch (std::exception& e)
    {
        std::cerr << "Exception in thread: " << e.what() << "\n";
    }
}

communication::WrapperMessage server::
build_response(communication::WrapperMessage &msg_req, task_manager& manager, std::string const& client_id, int64_t request_id)
{
    communication::WrapperMessage msg_resp;
    auto mut_resp = msg_resp.mutable_response();

    mut_resp->set_request_id(request_id);

    // SubmitRequest
    if (msg_req.request().has_submit())
    {
        std::cout << "\t\t\tbuilding submit message" << std::endl;

        auto task_args = msg_req.request().submit().task();
        int64_t n = msg_req.request().submit().task().n();

        // возвращает SubmitTaskResponse
        auto req_res = manager.submit_task(client_id, request_id, task_args, n);

        // ставим статус и submittedtaskid (если есть)
        mut_resp->mutable_submitresponse()->set_status(req_res.status());

        if (req_res.has_submittedtaskid())
        {
            mut_resp->mutable_submitresponse()->set_submittedtaskid(req_res.submittedtaskid());
        }
    }

    // SubscribeRequest
    else if (msg_req.request().has_subscribe())
    {
        int32_t task_id = msg_req.request().subscribe().taskid();

        // возвращает SubscribeResponse
        auto req_res = manager.subscribe(task_id);

        // ставим статус и value
        mut_resp->mutable_subscriberesponse()->set_status(req_res.status());
        mut_resp->mutable_subscriberesponse()->set_value(req_res.value());
    }

        // ListRequest
    else if (msg_req.request().has_list())
    {
        std::cout << "\t\t\tList message." << std::endl;

        // возвращает ListResponse
        auto req_res = manager.list_tasks();

        // ставим статус
        mut_resp->mutable_listresponse()->set_status(req_res.status());

        // размер массива tasks
        int32_t tasks_size = mut_resp->mutable_listresponse()->tasks_size();
        for (int i = 0; i < tasks_size; ++i)
        {
            mut_resp->mutable_listresponse()->mutable_tasks()->Add();

            auto mut_task_i = mut_resp->mutable_listresponse()->mutable_tasks(i);

            // ставим taskid и clientid
            mut_task_i->set_taskid(req_res.tasks(i).taskid());
            mut_task_i->set_clientid(req_res.tasks(i).clientid());

            // a, b, p, m, n
            mut_task_i->mutable_task()->mutable_a()->
                    set_value(req_res.tasks(i).task().a().value());

            mut_task_i->mutable_task()->mutable_b()->
                    set_value(req_res.tasks(i).task().b().value());

            mut_task_i->mutable_task()->mutable_p()->
                    set_value(req_res.tasks(i).task().p().value());

            mut_task_i->mutable_task()->mutable_m()->
                    set_value(req_res.tasks(i).task().m().value());

            mut_task_i->mutable_task()->set_n(req_res.tasks(i).task().n());
            // result
            if (req_res.tasks(i).has_result())
            {
                mut_task_i->set_result(req_res.tasks(i).has_result());
            }
        }
    }
    std::cout << "\t\t\treturning built message" << std::endl;
    return msg_resp;
}
