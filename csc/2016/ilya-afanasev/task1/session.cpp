#include <google/protobuf/io/coded_stream.h>
#include "session.h"

session::session(boost::asio::ip::tcp::socket socket, thread_pool& pool)
    : _socket(std::move(socket))
    , _pool(pool)
{
}

bool session::write_delimited_to(const google::protobuf::MessageLite& message
                                 , google::protobuf::io::ZeroCopyOutputStream* raw_utput)
{
  google::protobuf::io::CodedOutputStream output(raw_utput);

  const int size = message.ByteSize();
  output.WriteVarint32(size);

  uint8_t* buffer = output.GetDirectBufferForNBytesAndAdvance(size);
  if (buffer != NULL)
  {
    message.SerializeWithCachedSizesToArray(buffer);
  }
  else
  {
    message.SerializeWithCachedSizes(&output);
    if (output.HadError())
    {
      return false;
    }
  }

  return true;
}

bool session::read_varint(google::protobuf::io::ZeroCopyInputStream* rawInput, uint32_t& size)
{
  google::protobuf::io::CodedInputStream input(rawInput);
  return input.ReadVarint32(&size);
}

bool session::read_delimited_from(google::protobuf::io::ZeroCopyInputStream* raw_input
                                  , google::protobuf::MessageLite* message
                                  , uint32_t size)
{
  google::protobuf::io::CodedInputStream input(raw_input);

  google::protobuf::io::CodedInputStream::Limit limit = input.PushLimit(size);

  if (!message->MergeFromCodedStream(&input))
  {
    return false;
  }

  if (!input.ConsumedEntireMessage())
  {
    return false;
  }

  input.PopLimit(limit);

  return true;
}

void session::start()
{
  do_read();
}

void session::do_read()
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

void session::do_write(std::size_t length)
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