#include <functional>

#include "dispatcher.h"
#include "socket_init.h"

Dispatcher::Dispatcher()
: m_worker(std::bind(&Dispatcher::subscribe_callback, this, 
std::placeholders::_1, std::placeholders::_2, std::placeholders::_3))
{
  GOOGLE_PROTOBUF_VERIFY_VERSION;
}

Dispatcher::~Dispatcher()
{
  google::protobuf::ShutdownProtobufLibrary();
}

void Dispatcher::handle_connection(int sockfd)
{
  bool connect = true;
  SocketRW const * socket_rw = new SocketRW(sockfd);
  communication::WrapperMessage msg;
  
	while (connect)
	{
    if (socket_rw->read(msg))
    {
      if (is_submit_task(msg))
      {
        submit_task(msg, socket_rw);
      }
      else if (is_task_list(msg))
      {
        list_tasks(msg, socket_rw);
      }
      else if (is_subscribe(msg))
      {
        subscribe(msg, socket_rw);
      }
    }
    else
    {
      connect = false;
    }
	}
  
  delete socket_rw;
  close_sockfd(sockfd);
}

void Dispatcher::subscribe_callback(unsigned int task_id, int64_t request_id, int64_t result)
{
  m_mut.lock();
  
  if (m_socks_to_ids.find(task_id) != m_socks_to_ids.end())
  {
    communication::SubscribeResponse * response = communication::SubscribeResponse().New();
    response->set_status(communication::Status::OK);
    response->set_value(result);
    
    communication::WrapperMessage msg;
    msg.mutable_response()->set_request_id(request_id);
    msg.mutable_response()->set_allocated_subscriberesponse(response);
    
    auto const & socks = m_socks_to_ids[task_id];
    for (auto const sock : socks)
    {
      sock->write(msg);
    }
    
    m_socks_to_ids.erase(task_id);
  }
  
  m_mut.unlock();
}

bool Dispatcher::submit_task(communication::WrapperMessage const & msg_in, SocketRW const * socket_rw)
{
  communication::SubmitTask const & submitTask = msg_in.request().submit();
  
  unsigned int task_id = m_worker.handle_submit_task(submitTask, msg_in.request().client_id(), msg_in.request().request_id());
  
  communication::SubmitTaskResponse * response = communication::SubmitTaskResponse().New();
  response->set_status(communication::Status::OK);
  response->set_submittedtaskid(task_id);
  
  communication::WrapperMessage msg_out = create_serv_response(msg_in);
  msg_out.mutable_response()->set_allocated_submitresponse(response);
  
  return socket_rw->write(msg_out);
}

bool Dispatcher::list_tasks(communication::WrapperMessage const & msg_in, SocketRW const * socket_rw)
{
  std::vector<Task> tasks;
  m_worker.get_task_list(tasks);
  
  communication::ListTasksResponse * response = communication::ListTasksResponse().New();
  response->set_status(communication::Status::OK);
  
  for (auto const & task : tasks)
  {
    communication::ListTasksResponse_TaskDescription * desc = response->add_tasks();
    desc->set_taskid(task.id);
    desc->set_clientid(task.client_id);
    desc->mutable_task()->CopyFrom(task.args);
    if (task.finished)
    {
      desc->set_result(task.result);
    }
  }
  
  communication::WrapperMessage msg_out = create_serv_response(msg_in);
  msg_out.mutable_response()->set_allocated_listresponse(response);
  
  return socket_rw->write(msg_out);
}

bool Dispatcher::subscribe(communication::WrapperMessage const & msg_in, SocketRW const * socket_rw)
{
  bool res = false;
  communication::Subscribe const & subscribe = msg_in.request().subscribe();
  
  m_mut.lock();
  
  bool result_set;
  int64_t result;
  unsigned int task_id = subscribe.taskid();
  bool status = m_worker.subscribe(subscribe.taskid(), result_set, result);
  
  if (status)
  {
    if (result_set)
    {
      communication::SubscribeResponse * response = communication::SubscribeResponse().New();
      response->set_status(communication::Status::OK);
      response->set_value(result);
      
      communication::WrapperMessage msg_out = create_serv_response(msg_in);
      msg_out.mutable_response()->set_allocated_subscriberesponse(response);
      
      socket_rw->write(msg_out);
    }
    else
    {
      m_socks_to_ids[task_id].push_back(socket_rw);
      res = true;
    }
  }
  else
  {
    communication::SubscribeResponse * response = communication::SubscribeResponse().New();
    response->set_status(communication::Status::ERROR);
    
    communication::WrapperMessage msg_out = create_serv_response(msg_in);
    msg_out.mutable_response()->set_allocated_subscriberesponse(response);
    
    socket_rw->write(msg_out);
  }
  
  m_mut.unlock();
  
  return res;
}

communication::WrapperMessage Dispatcher::create_serv_response(communication::WrapperMessage const & serv_req)
{
  communication::WrapperMessage serv_response;
  serv_response.mutable_response()->set_request_id(serv_req.request().request_id());
  
  return serv_response;
}
