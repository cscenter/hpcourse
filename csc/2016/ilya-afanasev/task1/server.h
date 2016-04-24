#ifndef TASK1_SERVER_H
#define TASK1_SERVER_H

#include <boost/asio.hpp>
#include <memory>

#include "session.h"
#include "thread_pool.h"

class server
{
public:
  server(boost::asio::io_service& io_service, short port, thread_pool& pool);

private:
  void do_accept();

  void acceptor_callback(boost::system::error_code ec);

  boost::asio::ip::tcp::acceptor _acceptor;
  boost::asio::ip::tcp::socket _socket;
  thread_pool& _pool;
};


#endif //TASK1_SERVER_H
