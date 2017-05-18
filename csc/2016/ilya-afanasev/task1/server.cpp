#include "server.h"
#include <boost/bind.hpp>

server::server(boost::asio::io_service& io_service, short port, thread_pool& pool)
  : _acceptor(io_service, boost::asio::ip::tcp::endpoint(boost::asio::ip::tcp::v4(), port))
  , _socket(io_service)
  , _pool(pool)
{
  do_accept();
}

void server::acceptor_callback(boost::system::error_code ec)
{
  if (!ec)
  {
    std::make_shared<session>(std::move(_socket), boost::ref(_pool))->start();
  }

  do_accept();
}

void server::do_accept()
{
  _acceptor.async_accept(_socket, boost::bind(&server::acceptor_callback, this, boost::asio::placeholders::error));
}

