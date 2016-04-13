#include <cstdlib>
#include <iostream>
#include <memory>
#include <utility>
#include <boost/asio.hpp>
#include "protocol.pb.h"

using boost::asio::ip::tcp;

class session : public std::enable_shared_from_this<session>
{
public:
  session(tcp::socket socket) : _socket(std::move(socket))
  {
  }

  void start()
  {
    do_read();
  }

private:
  void do_read()
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

  void do_write(std::size_t length)
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

  tcp::socket _socket;
  enum
  {
    max_length = 1024
  };
  char data_[max_length];
};

class server
{
public:
  server(boost::asio::io_service& io_service, short port)
    : _acceptor(io_service, tcp::endpoint(tcp::v4(), port))
    , _socket(io_service)
  {
    do_accept();
  }

private:
  void do_accept()
  {
    _acceptor.async_accept(_socket, acceptor_callback);
  }

  void acceptor_callback(boost::system::error_code ec)
  {
    if (!ec)
    {
      std::make_shared<session>(std::move(_socket))->start();
    }

    do_accept();
  }

  tcp::acceptor _acceptor;
  tcp::socket _socket;
};

int main(int argc, char* argv[])
{
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  try
  {
    if (argc != 2)
    {
      std::cerr << "Usage: async_tcp_echo_server <port>" << std::endl;
      return 1;
    }

    boost::asio::io_service io_service;

    server s(io_service, std::atoi(argv[1]));

    io_service.run();
  }
  catch (std::exception& e)
  {
    std::cerr << "Exception: " << e.what() << std::endl;
  }

  google::protobuf::ShutdownProtobufLibrary();

  return 0;
}
