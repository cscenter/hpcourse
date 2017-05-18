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

bool session::read_varint(google::protobuf::io::ZeroCopyInputStream& raw_input, uint32_t& size)
{
  google::protobuf::io::CodedInputStream input(&raw_input);
  return input.ReadVarint32(&size);
}

bool session::read_delimited_from(google::protobuf::io::ZeroCopyInputStream& raw_input
                                  , google::protobuf::MessageLite& message
                                  , uint32_t size)
{
  google::protobuf::io::CodedInputStream input(&raw_input);

  google::protobuf::io::CodedInputStream::Limit limit = input.PushLimit(size);

  if (!message.MergeFromCodedStream(&input))
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
  auto read_callback = [this, self](boost::system::error_code ec, std::size_t read_bytes)
  {
    google::protobuf::io::ArrayInputStream raw_input(_data, max_length);

    std::size_t length = read_bytes + _last_size;
    if (!ec)
    {
      while (length > sizeof(uint32_t))
      {
        uint32_t next_message_size = 0;

        if (!read_varint(raw_input, next_message_size))
        {
          std::cerr << "An error occurred while reading input message. Can't read varint32" << std::endl;
          return;
        }

        uint32_t encoded_message_size = google::protobuf::io::CodedOutputStream::VarintSize32(next_message_size);

        if (length - encoded_message_size < next_message_size)
        {
          break;
        }
        else
        {
          communication::ServerRequest request;
          if (read_delimited_from(raw_input, request, next_message_size))
          {
            auto callback = [this](const communication::ServerResponse& response)
            {
              do_write(response);
            };

            _pool.put_command(request, callback);
            length -= encoded_message_size + next_message_size;
          }
          else
          {
            std::cerr << "An error occurred while reading input message. Wrong message format." << std::endl;
          }
        }
      }

      for (int i = 0; i < length; ++i)
      {
        _data[i] = _data[read_bytes - length + i];
      }

      _last_size = length;

      do_read();
    }
    else
    {
      std::cerr << "An error occurred while reading input message. Error code " << ec << std::endl;
    }
  };

  boost::asio::async_read(_socket
                          , boost::asio::buffer(_data, max_length)
                          , boost::asio::transfer_at_least(sizeof(uint32_t))
                          , read_callback);
}

void session::do_write(const communication::ServerResponse& response)
{
  auto self(shared_from_this());
  auto write_callback = [this, self](boost::system::error_code ec, std::size_t /*length*/)
  {
    if (ec)
    {
      std::cerr << "An error occurred while sending message to "
                << _socket.remote_endpoint().address().to_string() << ". error code = " << ec << std::endl;
    }
  };

  boost::asio::streambuf request;
  {
    std::ostream request_stream(&request);

    google::protobuf::io::OstreamOutputStream raw_output(&request_stream);

    write_delimited_to(response, &raw_output);
  }

  boost::asio::async_write(_socket, request, write_callback);
}