#ifndef TASK1_SESSION_H
#define TASK1_SESSION_H

#include <boost/asio.hpp>
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

  void do_write(std::size_t length);

  boost::asio::ip::tcp::socket _socket;

  enum
  {
    max_length = 1024
  };

  char data_[max_length];
  thread_pool& _pool;
};

#endif //TASK1_SESSION_H
