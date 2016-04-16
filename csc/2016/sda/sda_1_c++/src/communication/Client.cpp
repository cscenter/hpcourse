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

        if ( wrapper.has_request() ) {
            std::cout << "has request" << std::endl;
            ServerResponse resp = wrapper.response();
            std::cout << "request id " << resp.request_id() << std::endl;
        }
    }

    stop();
}
} // namespace communication
