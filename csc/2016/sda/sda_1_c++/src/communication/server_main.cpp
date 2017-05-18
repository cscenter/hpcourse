#include "Server.hpp"

using namespace boost::asio;
using namespace communication;

io_service service;
ip::tcp::acceptor acceptor(service, ip::tcp::endpoint(ip::tcp::v4(), 8001));

std::vector<boost::shared_ptr<boost::thread>> threads;

void cons(Server::ptr sock_list) {
    sock_list->start();
}

void handle_accept(Server::ptr socket_listener, const boost::system::error_code & err) {
    boost::shared_ptr<boost::thread> c = boost::shared_ptr<boost::thread>(new boost::thread(cons, socket_listener));
    threads.push_back(c);
    Server::ptr new_socket_listener = Server::get_new(service);
    acceptor.async_accept(new_socket_listener->sock(), boost::bind(handle_accept, new_socket_listener, _1));
}

int main(int argc, char* argv[]) {
    Server::ptr socket_listener = Server::get_new(service);
    acceptor.async_accept(socket_listener->sock(), boost::bind(handle_accept, socket_listener, _1));
    service.run();
}
