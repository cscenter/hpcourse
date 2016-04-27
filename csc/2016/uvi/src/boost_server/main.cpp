#include <cstdlib>
#include <iostream>
#include "server.hpp"
//using boost::asio::ip::tcp;
//
//const int max_length = 1024;
//
//void session(tcp::socket sock)
//{
//    try
//    {
//        for (;;)
//        {
//            char data[max_length];
//
//            // очищаем массив от старых данных
//            memset(data, 0, sizeof data);
//
//            boost::system::error_code error;
//            size_t length = sock.read_some(boost::asio::buffer(data), error);
//            if (error == boost::asio::error::eof)
//                break; // Connection closed cleanly by peer.
//            else if (error)
//                throw boost::system::system_error(error); // Some other error.
//
//            std::string tmp = data;
//            std::cout << "SERVER SESSION. ACCEPTED DATA: " << tmp << std::endl;
//
//            boost::asio::write(sock, boost::asio::buffer(data, length));
//        }
//    }
//    catch (std::exception& e)
//    {
//        std::cerr << "Exception in thread: " << e.what() << "\n";
//    }
//}
//
//void server(unsigned short port)
//{
//    boost::asio::io_service io_service;
//
//    tcp::acceptor a(io_service, tcp::endpoint(tcp::v4(), port));
//    for (;;)
//    {
//        tcp::socket sock(io_service);
//        a.accept(sock);
//        std::thread(session, std::move(sock)).detach();
//    }
//}
//
int main(int argc, char* argv[])
{
    try
    {
        if (argc != 2)
        {
            std::cerr << "Usage: blocking_tcp_echo_server <port>\n";
            return 1;
        }
        server sv;
        sv.server_impl(std::atoi(argv[1]));
    }
    catch (std::exception& e)
    {
        std::cerr << "Exception: " << e.what() << "\n";
    }

    return 0;
}
