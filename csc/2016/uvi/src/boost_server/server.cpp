#include <thread>
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
            // очищаем массив от старых данных
            memset(data_req, 0, sizeof data_req);

            boost::system::error_code error;
            // сервер читает данные
            size_t len = socket.read_some(boost::asio::buffer(data_req), error);

            // Connection closed cleanly by peer.
            if (error == boost::asio::error::eof) { break; }
            // Some other error.
            else if (error) { throw boost::system::system_error(error); }

            data_handler handler;
            auto msg_req = handler.parse(data_req, len);



            if (msg_req.has_request())
            {
                cout << endl << "Message from client has request" << endl;
                if (msg_req.request().has_submit())
                {
                    cout << "\tsubmit request" << endl;
                    cout << "params: " << msg_req.request().submit().task().a().value() << ' ' << msg_req.request().submit().task().b().value() << ' ' << msg_req.request().submit().task().p().value() << ' ' << msg_req.request().submit().task().m().value() << ' ' << msg_req.request().submit().task().n() << endl;
                }
                else if (msg_req.request().has_subscribe())
                {
                    cout << "\tsubscribe request" << endl;
                    cout << "task_id: " << msg_req.request().subscribe().taskid() << endl;
                }
                else if (msg_req.request().has_list()) { cout << "\tlist request" << endl; }
            }




            if (msg_req.has_request())
            {
                auto msg_resp = build_response(msg_req, manager, msg_req.request().client_id(), msg_req.request().request_id());

                if (msg_resp.has_response())
                {
                    cout << endl << "Message to client has response" << endl;
                    if (msg_resp.response().has_submitresponse()) { cout << "\tsubmit response" << endl; }
                    else if (msg_resp.response().has_subscriberesponse()) { cout << "\tsubscribe response" << endl; }
                    else if (msg_resp.response().has_listresponse()) { cout << "\tlist response" << endl; }
                }



                char data_resp[max_len];
                int len = handler.serialize(msg_resp, data_resp, max_len);


//                cout << "Current params in task vector before sending: " << manager.tasks[manager.tasks.size() - 1].get_a() << ' ' << manager.tasks[manager.tasks.size() - 1].get_b() << ' ' << manager.tasks[manager.tasks.size() - 1].get_p() << ' ' << manager.tasks[manager.tasks.size() - 1].get_m() << ' ' << manager.tasks[manager.tasks.size() - 1].get_n() << endl;

                // сервер посылает ответ
                cout << "SERVER IS SENDING RESPONSE..." << endl;
                boost::asio::write(socket, boost::asio::buffer(data_resp, len));
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
    //auto msg_resp.mutable_response() = msg_resp.mutable_response();

    msg_resp.mutable_response()->set_request_id(request_id);


//    cout << "Building response. Params in msg_req: " << msg_req.request().submit().task().a().value() << ' ' << msg_req.request().submit().task().b().value() << ' ' << msg_req.request().submit().task().p().value() << ' ' << msg_req.request().submit().task().m().value() << ' ' << msg_req.request().submit().task().n() << ' ' << endl;


    // SubmitRequest
    if (msg_req.request().has_submit())
    {
        std::cout << "\t\t\tbuilding submit message" << std::endl;

        auto task_args = msg_req.request().submit().task();
        int64_t n = msg_req.request().submit().task().n();

        // возвращает SubmitTaskResponse

//        cout << "TASK ARGS: " << task_args.a().value() << ' ' << task_args.b().value() << ' ' << task_args.p().value() << ' ' << task_args.m().value() << ' ' << task_args.n() << endl;
        auto req_res = manager.submit_task(client_id, request_id, task_args, n);

        // ставим статус и submittedtaskid (если есть)
        msg_resp.mutable_response()->mutable_submitresponse()->set_status(req_res.status());

        if (req_res.has_submittedtaskid())
        {
            msg_resp.mutable_response()->mutable_submitresponse()->set_submittedtaskid(req_res.submittedtaskid());
        }
    }

    // SubscribeRequest
    else if (msg_req.request().has_subscribe())
    {
        std::cout << "\t\t\tbuilding subscribe message" << std::endl;
        int32_t task_id = msg_req.request().subscribe().taskid();

        // возвращает SubscribeResponse
        auto req_res = manager.subscribe(task_id);

        // ставим статус и value
        msg_resp.mutable_response()->mutable_subscriberesponse()->set_status(req_res.status());
        msg_resp.mutable_response()->mutable_subscriberesponse()->set_value(req_res.value());
    }
        
        // ListRequest
    else if (msg_req.request().has_list())
    {
        std::cout << "\t\t\tList message." << std::endl;

        // возвращает ListResponse
        auto req_res = manager.list_tasks();

        // ставим статус
        msg_resp.mutable_response()->mutable_listresponse()->set_status(req_res.status());

        // размер массива tasks
        int32_t tasks_size = msg_resp.mutable_response()->mutable_listresponse()->tasks_size();
        for (int i = 0; i < tasks_size; ++i)
        {
            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks()->Add();

            //auto msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i) = msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i);

            // ставим taskid и clientid
            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->set_taskid(req_res.tasks(i).taskid());
            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->set_clientid(req_res.tasks(i).clientid());

            // a, b, p, m, n
            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->mutable_task()->mutable_a()->
                    set_value(req_res.tasks(i).task().a().value());

            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->mutable_task()->mutable_b()->
                    set_value(req_res.tasks(i).task().b().value());

            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->mutable_task()->mutable_p()->
                    set_value(req_res.tasks(i).task().p().value());

            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->mutable_task()->mutable_m()->
                    set_value(req_res.tasks(i).task().m().value());

            msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->mutable_task()->set_n(req_res.tasks(i).task().n());
            // result
            if (req_res.tasks(i).has_result())
            {
                msg_resp.mutable_response()->mutable_listresponse()->mutable_tasks(i)->set_result(req_res.tasks(i).has_result());
            }
        }
    }
//    cout << "Response built. Task_manager: tasks vector size: " << manager.tasks.size() << endl << "Params in tasks vector " << manager.tasks[0].get_a() << ' ' << manager.tasks[0].get_b() << ' ' << manager.tasks[0].get_p() << ' ' << manager.tasks[0].get_m() << ' ' << manager.tasks[0].get_n() << endl;

//    std::cout << "\t\t\treturning built message" << std::endl;
    return msg_resp;
}
