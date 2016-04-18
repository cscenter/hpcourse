#include "Server.hpp"

namespace communication {

// Server implementation

Server::Server(io_service& service) :
        SocketListenerBase(service)
{}

void Server::on_read_end(const error_code & err, size_t bytes) {
    if ( !err ) {
        std::string copy(m_read_buffer + 4, m_len);
        WrapperMessage wrapper;
        wrapper.ParseFromString(copy);
        if ( wrapper.has_request() ) {
            ServerRequest req = wrapper.request();
            if ( req.has_submit() ) {
                process_task(req);
            } else if ( req.has_subscribe() ) {
                process_subscription(req);
            } else if ( req.has_list() ) {
                process_get_list(req);
            }
        } else if ( wrapper.has_response() ) {
            std::cerr << "There are problems with requests. Please talk with client about it!" << std::endl;
        }
    }

    stop();
}

void Server::process_task(ServerRequest & req) {
    auto task = req.submit();
    auto req_id = req.request_id();
    if ( task.has_task() ) {

        int32_t task_id = ServerTask::push_front(task.task());


        SubmitTaskResponse *submit_task_resp = new SubmitTaskResponse();
        submit_task_resp->set_submittedtaskid(task_id);
        submit_task_resp->set_status(Status::OK);

        ServerResponse *server_resp = new ServerResponse();
        server_resp->set_request_id(req_id);
        server_resp->set_allocated_submitresponse(submit_task_resp);

        WrapperMessage wrap;
        wrap.set_allocated_response(server_resp);

        std::string output;
        if ( wrap.SerializeToString(&output) ) {
            this->set_message(output);
            this->write(output);
        }

        ServerTask::head.load()->run();
    }
}

void Server::process_subscription(ServerRequest & req) {
    int32_t task_id = req.subscribe().taskid();
    auto req_id = req.request_id();
    ServerTask* task = ServerTask::find(task_id);
    int64_t result = task->subscribe();

    SubscribeResponse *subs_resp = new SubscribeResponse();
    subs_resp->set_status(Status::OK);
    subs_resp->set_value(result);

    ServerResponse *resp = new ServerResponse();
    resp->set_allocated_subscriberesponse(subs_resp);
    resp->set_request_id(req_id);

    WrapperMessage wrap;
    wrap.set_allocated_response(resp);

    std::string output;
    if ( wrap.SerializeToString(&output) ) {
        this->set_message(output);
        this->write(output);
    }
}

void Server::process_get_list(ServerRequest & req) {
    auto status_list = ServerTask::get_list();
    auto req_id = req.request_id();

    ListTasksResponse *l_resp = new ListTasksResponse();
    for (auto t : status_list) {
        ListTasksResponse_TaskDescription* desc = l_resp->add_tasks();
        desc->Swap(&t);
    }

    ServerResponse *resp = new ServerResponse();
    resp->set_allocated_listresponse(l_resp);
    resp->set_request_id(req_id);

    WrapperMessage wrap;
    wrap.set_allocated_response(resp);

    std::string output;
    if ( wrap.SerializeToString(&output) ) {
        this->set_message(output);
        this->write(output);
    }
}



// @end of server implementation


} // namespace communication
