#ifndef TASK1_SESSION_H
#define TASK1_SESSION_H

#include <boost/asio.hpp>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <memory>

#include "protocol.pb.h"
#include "thread_pool.h"

class session : public std::enable_shared_from_this<session>
{
public:
  session(boost::asio::ip::tcp::socket socket, thread_pool& pool);

  void start();

private:
  void do_read();

  void do_write(const communication::ServerResponse& response);

  bool write_delimited_to(const google::protobuf::MessageLite& message
                          , google::protobuf::io::ZeroCopyOutputStream* raw_utput);

  bool read_delimited_from(google::protobuf::io::ZeroCopyInputStream& raw_input
                           , google::protobuf::MessageLite& message
                           , uint32_t size);

  bool read_varint(google::protobuf::io::ZeroCopyInputStream& raw_input, uint32_t& size);

  boost::asio::ip::tcp::socket _socket;

  enum
  {
    max_length = 1024
  };

  char _data[max_length];
  int _last_size;

  thread_pool& _pool;

};

#endif //TASK1_SESSION_H
