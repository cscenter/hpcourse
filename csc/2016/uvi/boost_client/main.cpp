#include <cstdlib>
#include <cstring>
#include <iostream>
#include "client.hpp"

using boost::asio::ip::tcp;

int main(int argc, char* argv[])
{

    if (argc != 3)
    {
        std::cerr << "Usage: blocking_tcp_echo_client <host> <port>\n";
        return 1;
    }
    client cl;
    cl.client_impl(argv[1], argv[2]);


    return 0;
}
