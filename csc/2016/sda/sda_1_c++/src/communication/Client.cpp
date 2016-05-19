#include "Client.hpp"

namespace communication {

Client::Client(io_service& service) :
        SocketListenerBase(service)
{}

void Client::on_read_end(const error_code & err, size_t bytes) {
    if ( !err ) {
        std::string copy(m_read_buffer + 4, m_len);

        WrapperMessage wrapper;
        wrapper.ParseFromString(copy);

        if ( wrapper.has_response() ) {
            ServerResponse resp = wrapper.response();
            std::cout << "request id " << resp.request_id() << std::endl;
            if ( resp.has_submitresponse() ) {
                SubmitTaskResponse submitted_task_resp = resp.submitresponse();

                if ( submitted_task_resp.has_submittedtaskid() ) {
                    std::cout << "task id " << submitted_task_resp.submittedtaskid() << std::endl;
                }
            }
            else if ( resp.has_subscriberesponse() )
            {
                SubscribeResponse s = resp.subscriberesponse();

                if ( s.has_value() ) {
                    std::cout << "task result " << s.value() << std::endl;
                }
            } else if ( resp.has_listresponse() )
            {
                ListTasksResponse tasks_list = resp.listresponse();
                int size = tasks_list.tasks_size();
                std::cout << "list response size " << size << std::endl;

                for (int i = 0; i < size; ++i) {
                    ListTasksResponse_TaskDescription task = tasks_list.tasks(i);
                    std::cout << "task id is " << task.taskid() << std::endl;
                }
            }
        }
    }

    stop();
}
} // namespace communication
