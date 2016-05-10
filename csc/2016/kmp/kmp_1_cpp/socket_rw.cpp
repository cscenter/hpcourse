#include <memory>

#include <sys/types.h>
#include <sys/socket.h>

#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>

#include "socket_rw.h"

#define MAX_VARINT_LENGTH 4

using namespace google::protobuf::io;

SocketRW::SocketRW(int sockfd)
: m_sockfd(sockfd)
{ }

bool SocketRW::read(communication::WrapperMessage & msg) const
{
  size_t length = get_var_length();
  
  if (length == 0)
  {
    return false;
  }
  
  std::unique_ptr<unsigned char[]> buffer(new unsigned char[length]);
  int n = recv(m_sockfd, buffer.get(), length, MSG_WAITALL);
  if (n == 0)
  {
    return false;
  }
  std::unique_ptr<CodedInputStream>
    coded_data(new CodedInputStream(buffer.get(), length));
  
  msg.Clear();
  msg.ParseFromCodedStream(coded_data.get());
  
  return true;
}

bool SocketRW::write(communication::WrapperMessage const & msg) const
{
  size_t length = msg.ByteSize();
  std::unique_ptr<unsigned char[]>
    buffer(new unsigned char[length + MAX_VARINT_LENGTH]);
  
  std::unique_ptr<ArrayOutputStream>
    aos(new ArrayOutputStream(buffer.get(), length + MAX_VARINT_LENGTH));
  
  std::unique_ptr<CodedOutputStream>
    output_stream(new CodedOutputStream(aos.get()));
  output_stream->WriteTag(length);
  msg.SerializeToCodedStream(output_stream.get());
  
  send(m_sockfd, buffer.get(), output_stream->ByteCount(), MSG_DONTWAIT);
  
  return true;
}

size_t SocketRW::get_var_length() const
{
  size_t length = 0;
  int sz = 1;
  unsigned char length_buf[MAX_VARINT_LENGTH];
  memset(length_buf, 0, MAX_VARINT_LENGTH);
  int n = 1;
  
  for (; n != 0 && length == 0 && sz != MAX_VARINT_LENGTH + 1; ++sz)
  {
    n = recv(m_sockfd, length_buf + sz - 1, 1, MSG_WAITALL);
    CodedInputStream coded_length(length_buf, sz);
    
    length = coded_length.ReadTag();
  }
  
  if (n == 0)
  {
    return 0;
  }
  
  return length;
}
