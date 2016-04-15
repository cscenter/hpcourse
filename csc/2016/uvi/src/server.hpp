#ifndef PARALLEL_SERVER_HPP
#define PARALLEL_SERVER_HPP

#include <cstdlib>
#include <iostream>
#include <thread>
#include <utility>
#include <boost/asio.hpp>
#include <string>
#include "protocol.pb.h"
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
using boost::asio::ip::tcp;

struct server {
public:
    server(unsigned short port);
private:
    void server_impl(boost::asio::io_service& io_service, unsigned short port);
    void session(tcp::socket sock);
    void write_serialized_message(communication::WrapperMessage& msg,
                                  google::protobuf::io::ArrayOutputStream* aos,
                                  uint8_t* buffer);
};

#endif //PARALLEL_SERVER_HPP
