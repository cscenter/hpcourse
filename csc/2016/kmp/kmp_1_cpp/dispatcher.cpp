#include <iostream>
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
  std::cout << "handle_connection 1" << std::endl;
  SocketRW * socket_rw = new SocketRW(sockfd);
  std::cout << "handle_connection 2" << std::endl;
  communication::WrapperMessage msg;
  
	while (connect)
	{
    if (socket_rw->read(msg))
    {
      if (is_submit_task(msg))
      {
        submit_task(msg, socket_rw);
        std::cout << "Submitted" << std::endl;
      }
      else if (is_task_list(msg))
      {
        std::cout << "Task list message" << std::endl;
        list_tasks(msg, socket_rw);
      }
      else if (is_subscribe(msg))
      {
        std::cout << "Subscribe message" << std::endl;
        subscribe(msg, socket_rw);
      }
      else
      {
        std::cout << "Unknown message type" << std::endl;
      }
    }
    else
    {
      std::cout << "Client dropped the connection" << std::endl;
      connect = false;
    }
	}
  
  delete socket_rw;
  close_sockfd(sockfd);
}

void Dispatcher::subscribe_callback(unsigned int task_id, int64_t request_id, int64_t result)
{
  m_mut.lock();
  
  std::cout << "subscribe_callback on task id " << task_id << std::endl;
  
  if (m_socks_to_ids.find(task_id) != m_socks_to_ids.end())
  {
    communication::SubscribeResponse * response = communication::SubscribeResponse().New();
    response->set_status(communication::Status::OK);
    response->set_value(result);
    
    communication::WrapperMessage msg;
    msg.mutable_response()->set_request_id(request_id);
    msg.mutable_response()->set_allocated_subscriberesponse(response);
    
    std::cout << "subscribe_callback ByteSize " << msg.ByteSize() << std::endl;
    
    std::vector<SocketRW *> & socks = m_socks_to_ids[task_id];
    for (std::vector<SocketRW *>::iterator sock = socks.begin(); 
    sock != socks.end(); 
    ++sock)
    {
      std::cout << "write subscribe to socket" << std::endl;
      (*sock)->write(msg);
      
      std::cout << "wrote to socket" << std::endl;
    }
    
    m_socks_to_ids.erase(task_id);
    std::cout << "id " << task_id << " ERASED from map" << std::endl;
  }
  
  m_mut.unlock();
}

bool Dispatcher::submit_task(communication::WrapperMessage const & msg_in, SocketRW * socket_rw)
{
  communication::SubmitTask const & submitTask = msg_in.request().submit();
    
  std::cout << "a = " << submitTask.task().a().value() << std::endl;
  std::cout << "b = " << submitTask.task().b().value() << std::endl;
  std::cout << "p = " << submitTask.task().p().value() << std::endl;
  std::cout << "m = " << submitTask.task().m().value() << std::endl;
  std::cout << "n = " << submitTask.task().n() << std::endl;
  
  unsigned int task_id = m_worker.handle_submit_task(submitTask, msg_in.request().client_id(), msg_in.request().request_id());
  
  communication::SubmitTaskResponse * response = communication::SubmitTaskResponse().New();
  response->set_status(communication::Status::OK);
  response->set_submittedtaskid(task_id);
  
  communication::WrapperMessage msg_out = create_serv_response(msg_in);
  msg_out.mutable_response()->set_allocated_submitresponse(response);
  
  return socket_rw->write(msg_out);
}

bool Dispatcher::list_tasks(communication::WrapperMessage const & msg_in, SocketRW * socket_rw)
{
  std::vector<Task> tasks;
  m_worker.get_task_list(tasks);
  
  communication::ListTasksResponse * response = communication::ListTasksResponse().New();
  response->set_status(communication::Status::OK);
  
  size_t sz = tasks.size();
  for (size_t i = 0; i != sz; ++i)
  {
    communication::ListTasksResponse_TaskDescription * desc = response->add_tasks();
    desc->set_taskid(tasks[i].id);
    desc->set_clientid(tasks[i].client_id);
    desc->mutable_task()->CopyFrom(tasks[i].args);
    if (tasks[i].finished)
    {
      desc->set_result(tasks[i].result);
    }
  }
  
  communication::WrapperMessage msg_out = create_serv_response(msg_in);
  msg_out.mutable_response()->set_allocated_listresponse(response);
  
  return socket_rw->write(msg_out);
}

bool Dispatcher::subscribe(communication::WrapperMessage const & msg_in, SocketRW * socket_rw)
{
  bool res = false;
  communication::Subscribe const & subscribe = msg_in.request().subscribe();
  
  m_mut.lock();
  
  bool result_set;
  int64_t result;
  unsigned int task_id = subscribe.taskid();
  bool status = m_worker.subscribe(subscribe.taskid(), result_set, result);
  
  std::cout << "id: " << task_id << std::endl;
  
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
      std::cout << "id " << task_id << " ADDED to map" << std::endl;
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
