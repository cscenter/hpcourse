#ifndef __SERVER_ASYNC_SERVICE__
#define __SERVER_ASYNC_SERVICE__

#include <boost/asio.hpp>
#include <boost/noncopyable.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <memory>
#include <google/protobuf/message.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/text_format.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
#include "../metadata/protocol.pb.h"

class TaskQueue;
class AsyncService : public boost::enable_shared_from_this<AsyncService>, boost::noncopyable
{
    AsyncService(boost::asio::io_service & service, TaskQueue & queue);

public:
    typedef boost::system::error_code error_code;
    typedef boost::shared_ptr<AsyncService> ptr_on_async_service;

public:
    void start();
    void send_message(const communication::WrapperMessage & message);
    boost::asio::ip::tcp::socket & get_socket();
    static ptr_on_async_service get_new_ptr_on_async_service(boost::asio::io_service & service, TaskQueue & queue);
    ~AsyncService();

private:
    void do_read();
    google::protobuf::uint32 get_int32(const char * buffer);
    void read_size_done(const boost::system::error_code & err, size_t bytes);
    void read_body_done(const boost::system::error_code & err, size_t bytes);

private:
    const static char SIZE_INT32 = 4;
    std::vector<char> buffer_size_;
    std::vector<char> buffer_body_;
    boost::asio::ip::tcp::socket sock_;
    TaskQueue & queue_;
};

#endif //__SERVER_ASYNC_SERVICE__
