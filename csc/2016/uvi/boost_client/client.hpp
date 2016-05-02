#ifndef BOOST_CLIENT_CLIENT_HPP
#define BOOST_CLIENT_CLIENT_HPP

#include <boost/asio.hpp>
#include <string>
#include "../boost_server/protocol.pb.h"

using boost::asio::ip::tcp;

struct client {

public:
    client();
    client(client const &) = delete;
    client& operator=(client const &) = delete;

    void client_impl(std::string host, std::string port);

private:
    void build_request(communication::WrapperMessage& msg_req, int req_type);
    void send_resquest(tcp::socket& socket, communication::WrapperMessage const& msg_req);
    void get_server_response(tcp::socket& socket);
    void print_response(communication::WrapperMessage const& msg) const;
};
#endif //BOOST_CLIENT_CLIENT_HPP
