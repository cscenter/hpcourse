#ifndef PARALLEL_CLIENT_HPP
#define PARALLEL_CLIENT_HPP

#include <cstdlib>
#include <cstring>
#include <iostream>
#include <boost/asio.hpp>
#include "protocol.pb.h"
using boost::asio::ip::tcp;
struct client {

public:

    client();
    client(client const &) = delete;
    client& operator=(client const &) = delete;
    void run_request_session(std::string host, std::string port);
private:

    std::pair<int64_t, int32_t> input_submit_task_param(char param);
    void request(tcp::socket& sock);
    void send_request(tcp::socket & sock, communication::WrapperMessage const& msg);
    void get_response(tcp::socket& sock);
    void print_response(communication::WrapperMessage const &msg);
};

#endif //PARALLEL_CLIENT_HPP
