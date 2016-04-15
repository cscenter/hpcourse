#include "server.hpp"
#include "task_manager.hpp"

server::server(unsigned short port) {

    try {
        boost::asio::io_service io_service;
        server_impl(io_service, port);
    }
    // где-то надо исключение ловить
    catch (std::exception& e) {
        std::cerr << "Exception: " << e.what() << "\n";
    }

}

void server::server_impl(boost::asio::io_service &io_service, unsigned short port) {

    tcp::acceptor a(io_service, tcp::endpoint(tcp::v4(), port));
    for (;;) {
        tcp::socket sock(io_service);
        a.accept(sock);
        std::thread(&server::session, this, std::move(sock)).detach();
    }
}

void server::session(tcp::socket sock) {

    const uint32_t max_length = 1024;

    try {
        for (;;) {
            uint8_t data[max_length];

            boost::system::error_code error;
            
            // сервер читает данные
            const size_t length = sock.read_some(boost::asio::buffer(data), error);

            std::cout << "READ LENGTH: " << length << std::endl;
            if (error == boost::asio::error::eof) {
                break; // Connection closed cleanly by peer.
            }
            else if (error) {
                throw boost::system::system_error(error); // Some other error.
            }

            google::protobuf::io::ArrayInputStream ais(data, length);
            google::protobuf::io::CodedInputStream cis(&ais);

            uint32_t msg_length = 0;
            task_manager manager;

            // если несколько сообщений пришли с сокета
            while(cis.ReadVarint32(&msg_length)) {

                auto limit = cis.PushLimit(msg_length);

                communication::WrapperMessage msg_request;
                msg_request.ParseFromCodedStream(&cis);
                cis.PopLimit(limit);

                std::cout << "Client message recieved." << std::endl;
                std::cout << "Message size: " << sizeof(msg_request) << std::endl;
                std::cout << "Is request a submit request? ";
                std::cout << msg_request.request().has_submit() << std::endl << std::endl;

                std::cout << "Is request a list request? ";
                std::cout << msg_request.request().has_list() << std::endl << std::endl;

                if (msg_request.has_request()) {

                    std::cout << "\tRequest message." << std::endl;
                    std::string client_id = msg_request.request().client_id();
                    int64_t request_id = msg_request.request().request_id();

                    communication::WrapperMessage msg_response;

                    auto mut_resp_ptr = msg_response.mutable_response();

                    // не уверен, что это нужный request_id
                    mut_resp_ptr->set_request_id(request_id);

                    // SubmitRequest
                    if(msg_request.request().has_submit()) {

                        std::cout << "\t\tSubmit message." << std::endl;
                        auto task_args = msg_request.request().submit().task();
                        int64_t n = msg_request.request().submit().task().n();

                        // возвращает SubmitTaskResponse
                        auto req_res = manager.submit_task(client_id, request_id, task_args, n);

                        // ставим статус и submittedtaskid (если есть)
                        mut_resp_ptr->mutable_submitresponse()->
                                set_status(req_res.status());
                        if (req_res.has_submittedtaskid()) {
                            mut_resp_ptr->mutable_submitresponse()->
                                    set_submittedtaskid(req_res.submittedtaskid());
                        }
                    }
                    // SubscribeRequest
                    else if (msg_request.request().has_subscribe()) {

                        int32_t task_id = msg_request.request().subscribe().taskid();

                        // возвращает SubscribeResponse
                        auto req_res = manager.subscribe(task_id);

                        // ставим статус и value
                        mut_resp_ptr->mutable_subscriberesponse()->set_status(req_res.status());
                        mut_resp_ptr->mutable_subscriberesponse()->set_value(req_res.value());
                    }
                    // ListRequest
                    else if (msg_request.request().has_list()) {

                        // возвращает ListResponse
                        auto req_res = manager.list_tasks();

                        // ставим статус
                        mut_resp_ptr->mutable_listresponse()->set_status(req_res.status());

                        // размер массива tasks
                        int32_t tasks_size = mut_resp_ptr->mutable_listresponse()->tasks_size();
                        for (int i = 0; i < tasks_size; ++i) {

                            mut_resp_ptr->mutable_listresponse()->mutable_tasks()->Add();

                            auto mut_task_ptr_i = mut_resp_ptr->mutable_listresponse()->mutable_tasks(i);
                            
                            // ставим taskid и clientid
                            mut_task_ptr_i->set_taskid(req_res.tasks(i).taskid());
                            mut_task_ptr_i->set_clientid(req_res.tasks(i).clientid());

                            // a, b, p, m, n
                            mut_task_ptr_i->mutable_task()->mutable_a()->
                                    set_value(req_res.tasks(i).task().a().value());
                            mut_task_ptr_i->mutable_task()->mutable_b()->
                                    set_value(req_res.tasks(i).task().b().value());
                            mut_task_ptr_i->mutable_task()->mutable_p()->
                                    set_value(req_res.tasks(i).task().p().value());
                            mut_task_ptr_i->mutable_task()->mutable_m()->
                                    set_value(req_res.tasks(i).task().m().value());
                            mut_task_ptr_i->mutable_task()->set_n(req_res.tasks(i).task().n());
                            // result
                            if (req_res.tasks(i).has_result()) {
                                mut_task_ptr_i->set_result(req_res.tasks(i).has_result());
                            }
                        }
                    }

                    uint8_t data_response [max_length];
                    google::protobuf::io::ArrayOutputStream aos(data_response, max_length);
                    write_serialized_message(msg_response, &aos, data_response);

                    // сервер посылает ответ
                    boost::asio::write(sock,
                                       boost::asio::buffer(data_response,
                                                           msg_response.ByteSize()));
                }
            }
        }
    }

    catch (std::exception& e) {
        std::cerr << "Exception in thread: " << e.what() << "\n";
    }
}

void server::write_serialized_message(communication::WrapperMessage &msg,
                                      google::protobuf::io::ArrayOutputStream* aos,
                                      uint8_t* buffer) {

    // We create a new coded stream for each message.  Don't worry, this is fast.
    google::protobuf::io::CodedOutputStream cos(aos);

    // Write the size.
    const int32_t size = msg.ByteSize();
    cos.WriteVarint32(size);

    buffer = cos.GetDirectBufferForNBytesAndAdvance(size);
    msg.SerializeWithCachedSizesToArray(buffer);
}
