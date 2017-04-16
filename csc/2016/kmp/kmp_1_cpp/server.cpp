#include <iostream>
#include <thread>
#include <string>

#include "dispatcher.h"
#include "socket_init.h"
#include "error.h"

int main(int argc, char **argv)
{
	if (argc < 2)
	{
		error("no port provided");
	}
  
  int sockfd = init_sockfd(std::stoi(argv[1]));
  
  Dispatcher dsp;
  while (true)
  {
    int newsockfd = accept_conn(sockfd);
    if (newsockfd < 0)
    {
      error("on accept");
    }
    
    std::thread(&Dispatcher::handle_connection, &dsp, newsockfd).detach();
  }
	
	close_sockfd(sockfd);

	return 0;
}
