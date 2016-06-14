#include "AsyncService.h"
#include "TaskQueue.h"
#include "../metadata/protocol.pb.h"
AsyncService::AsyncService(boost::asio::io_service & service, TaskQueue & queue) : sock_(service), queue_(queue)
{
}

void AsyncService::start()
{
    do_read();
}

void AsyncService::send_message(const communication::WrapperMessage & message)
{
    static int id = 0;
    std::cout << "Qsize[" << id++ <<  "] = " << queue_.size() << std::endl;
    int size = message.ByteSize() + 4;
    std::vector<char> pkt(size);
    google::protobuf::io::ArrayOutputStream aos(&pkt[0], size);
    google::protobuf::io::CodedOutputStream *coded_output = new google::protobuf::io::CodedOutputStream(&aos);
    coded_output->WriteVarint32(message.ByteSize());
    message.SerializeToCodedStream(coded_output);
    sock_.async_write_some( boost::asio::buffer(pkt), [](const boost::system::error_code & err, size_t bytes) -> void { });
}

boost::asio::ip::tcp::socket & AsyncService::get_socket()
{ 
    return sock_; 
}

AsyncService::ptr_on_async_service AsyncService::get_new_ptr_on_async_service(boost::asio::io_service & service, TaskQueue & queue)
{
    ptr_on_async_service ptr(new AsyncService(service, queue));
    return ptr; 
}

void AsyncService::do_read()
{
    buffer_size_.resize(4);
    boost::asio::async_read(sock_, boost::asio::buffer(buffer_size_), boost::asio::transfer_exactly(SIZE_INT32),
                            std::bind(&AsyncService::read_size_done, shared_from_this(), std::placeholders::_1, std::placeholders::_2));
}

google::protobuf::uint32 AsyncService::get_int32(const char * buffer)
{
    google::protobuf::uint32 size;
    google::protobuf::io::ArrayInputStream ais(buffer, SIZE_INT32);
    google::protobuf::io::CodedInputStream coded_input(&ais);
    coded_input.ReadVarint32(&size);
    return size;
}

void AsyncService::read_size_done(const boost::system::error_code & err, size_t bytes)
{
    if (!err) {
      google::protobuf::uint32 size = get_int32(&buffer_size_[0]);
      buffer_body_.resize(size);
      boost::asio::async_read(sock_, boost::asio::buffer(buffer_body_), boost::asio::transfer_exactly(size),
                              std::bind(&AsyncService::read_body_done, shared_from_this(), std::placeholders::_1, std::placeholders::_2));
    } else {
        sock_.close();
    }
}

AsyncService::~AsyncService()
{
    sock_.close();
}

void AsyncService::read_body_done(const boost::system::error_code & err, size_t bytes)
{
    if (!err) {
      std::vector<char> message_buffer;
      message_buffer.insert(message_buffer.end(), buffer_size_.begin(), buffer_size_.end()); 
      message_buffer.insert(message_buffer.end(), buffer_body_.begin(), buffer_body_.end()); 
      google::protobuf::uint32 size = message_buffer.size();
      communication::WrapperMessage message;
      google::protobuf::io::ArrayInputStream ais(&message_buffer[0], size);
      google::protobuf::io::CodedInputStream coded_input(&ais);
      coded_input.ReadVarint32(&size);
      google::protobuf::io::CodedInputStream::Limit msgLimit = coded_input.PushLimit(size);
      message.ParseFromCodedStream(&coded_input);
      coded_input.PopLimit(msgLimit);
      queue_.add_message(message, shared_from_this());
      do_read();
    } else {
        sock_.close();
    }
}
