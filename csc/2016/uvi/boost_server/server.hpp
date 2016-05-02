#ifndef BOOST_SERVER_SERVER_HPP
#define BOOST_SERVER_SERVER_HPP

#include <boost/asio.hpp>
#include "protocol.pb.h"
#include "task_manager.hpp"

using boost::asio::ip::tcp;

struct server {
public:

    server();
    server(server const&) = delete;
    server& operator=(server const&) = delete;

    void server_impl(unsigned short port);
    void session(tcp::socket socket);

private:
    communication::WrapperMessage
    build_response(communication::WrapperMessage& msg_req, task_manager& manager, std::string const& client_id, int64_t request_id);
};


#endif //BOOST_SERVER_SERVER_HPP
