#include "session.h"

session::session(boost::asio::ip::tcp::socket socket, thread_pool& pool)
    : _socket(std::move(socket))
    , _pool(pool)
{
}

void session::start()
{
  do_read();
}

void session::do_read()
{
  auto self(shared_from_this());
  auto read_some_callback = [this, self](boost::system::error_code ec, std::size_t length)
  {
    if (!ec)
    {
      do_write(length);
    }
  };
  _socket.async_read_some(boost::asio::buffer(data_, max_length), read_some_callback);
}

void session::do_write(std::size_t length)
{
  auto self(shared_from_this());
  auto write_callback = [this, self](boost::system::error_code ec, std::size_t /*length*/)
  {
    if (!ec)
    {
      do_read();
    }
  };

  boost::asio::async_write(_socket, boost::asio::buffer(data_, length), write_callback);
}