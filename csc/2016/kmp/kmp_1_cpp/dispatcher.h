#ifndef DISPATCHER_H_
#define DISPATCHER_H_

#include <mutex>
#include <unordered_map>
#include <vector>

#include "worker.h"
#include "protocol.pb.h"

#include "socket_rw.h"

class Dispatcher
{
public:
  Dispatcher();
  ~Dispatcher();
  
  void handle_connection(int sockfd);
  void subscribe_callback(unsigned int task_id, int64_t request_id, int64_t result);
  
private:
  bool submit_task(communication::WrapperMessage const & msg_in, SocketRW * socket_rw);
  bool list_tasks(communication::WrapperMessage const & msg_in, SocketRW * socket_rw);
  bool subscribe(communication::WrapperMessage const & msg_in, SocketRW * socket_rw);
  communication::WrapperMessage create_serv_response(communication::WrapperMessage const & serv_req);
  
  inline bool get_message_from_bytes(char const * input, size_t len, communication::WrapperMessage & out) const
  {
    return out.ParseFromArray(input, len);
  }
  
  inline bool is_submit_task(communication::WrapperMessage const & msg) const { return msg.request().has_submit(); }
  inline bool is_task_list(communication::WrapperMessage const & msg) const { return msg.request().has_list(); }
  inline bool is_subscribe(communication::WrapperMessage const & msg) const { return msg.request().has_subscribe(); }

private:
  Worker m_worker;
  std::mutex m_mut;
  std::unordered_map<unsigned int, std::vector<SocketRW *> > m_socks_to_ids;
};

#endif
