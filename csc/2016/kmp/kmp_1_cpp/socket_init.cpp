#include <iostream>
#include <strings.h>

#include <unistd.h>
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>

#include "socket_init.h"
#include "error.h"

int init_sockfd(int portno)
{
  int sockfd = socket(AF_INET, SOCK_STREAM, 0);
	if (sockfd < 0)
	{
		error("fail when opening socket");
	}
  
  struct sockaddr_in serv_addr;
	bzero((char *) &serv_addr, sizeof(serv_addr));

	serv_addr.sin_family = AF_INET;
	serv_addr.sin_addr.s_addr = INADDR_ANY;
	serv_addr.sin_port = htons(portno);
	if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0)
	{
		error("fail on binding");
	}

	listen(sockfd, 10);
  
  return sockfd;
}

int accept_conn(int sockfd)
{
  struct sockaddr_in cli_addr;
	socklen_t clilen = sizeof(cli_addr);
  
  return accept(sockfd, (struct sockaddr *) &cli_addr, &clilen);
}

void close_sockfd(int sockfd)
{
  close(sockfd);
}
