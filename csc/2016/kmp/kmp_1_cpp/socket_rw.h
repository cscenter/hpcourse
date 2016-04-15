#ifndef SOCKET_RW_
#define SOCKET_RW_

#include "protocol.pb.h"

class SocketRW
{
public:
  SocketRW(int sockfd);
  
  bool read(communication::WrapperMessage & msg) const;
  bool write(communication::WrapperMessage const & msg) const;

private:
  size_t get_var_length() const;
  
private:
  int m_sockfd;
};

#endif
