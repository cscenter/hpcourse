#include <cstdlib>
#include <iostream>
#include <memory>
#include <utility>
#include <boost/asio.hpp>

#include "protocol.pb.h"
#include "server.h"
#include "thread_pool.h"

int main(int argc, char* argv[])
{
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  try
  {
    if (argc != 2)
    {
      std::cerr << "Usage: exe_path <port>" << std::endl;
      return EXIT_FAILURE;
    }

    thread_pool pool;

    boost::asio::io_service io_service;

    server s(io_service, std::atoi(argv[1]), pool);

    io_service.run();
  }
  catch (std::exception& e)
  {
    std::cerr << "Exception: " << e.what() << std::endl;
  }

  google::protobuf::ShutdownProtobufLibrary();

  return EXIT_SUCCESS;
}
