#include <cstdlib>
#include <iostream>
#include <memory>
#include <utility>
#include <boost/asio.hpp>
#include "protocol.pb.h"
#include "server.h"

int main(int argc, char* argv[])
{
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  try
  {
    if (argc != 2)
    {
      std::cerr << "Usage: exe_path <port>" << std::endl;
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
