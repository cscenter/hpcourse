#ifndef SOCKET_INIT_
#define SOCKET_INIT_

int init_sockfd(int portno);
int accept_conn(int sockfd);
void close_sockfd(int sockfd);

#endif
