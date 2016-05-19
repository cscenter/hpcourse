#ifndef SOCKETLISTENERBASE
#define SOCKETLISTENERBASE

#include <boost/thread.hpp>
#include <boost/bind.hpp>
#include <boost/asio.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

#include <string>
#include <iostream>

namespace communication {

using namespace boost::asio;
using namespace boost::posix_time;

#define MEM_FN(x)       boost::bind(&self_type::x, shared_from_this())
#define MEM_FN1(x,y)    boost::bind(&self_type::x, shared_from_this(),y)
#define MEM_FN2(x,y,z)  boost::bind(&self_type::x, shared_from_this(),y,z)

class SocketListenerBase : public boost::enable_shared_from_this<SocketListenerBase>,
                           boost::noncopyable
{
    typedef SocketListenerBase self_type;

public:
    SocketListenerBase(io_service& service);

    void start();
    void start(ip::tcp::endpoint ep);
    void stop();
    ip::tcp::socket & sock();
    void set_message(const std::string & msg);

protected:

    typedef boost::system::error_code error_code;

    virtual void on_read_end(const error_code & err, size_t bytes) = 0;

    void on_connect(const error_code & err);
    void on_write_end(const error_code & err, size_t bytes);
    void read();
    void write(const std::string & msg);
    size_t read_complete(const error_code & err, size_t bytes);

protected:
    enum { max_len = 8024 };

    ip::tcp::socket m_sock;
    char            m_read_buffer[max_len];
    char            m_write_buffer[max_len];
    bool            m_started;
    size_t          m_len = 0;
    std::string     m_message;
}; // SocketListenerBase


} // namespace communication


#endif /* end of include guard: SocketListenerBase */
