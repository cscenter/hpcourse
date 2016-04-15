#include "client.hpp"

int main(int argc, char* argv[]) {

    if (argc != 3) {
        std::cerr << "Usage: blocking_tcp_echo_client <host> <port>\n";
        return 1;
    }

    client cl;
    cl.run_request_session(argv[1], argv[2]);
}