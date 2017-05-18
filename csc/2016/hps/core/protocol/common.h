#ifndef __PROTOCOL_COMMON__ 
#define __PROTOCOL_COMMON__

#include "../metadata/protocol.pb.h"
#include <boost/asio.hpp>
#include <google/protobuf/message.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/text_format.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>

namespace protocol {

bool is_request(const communication::WrapperMessage & message);
bool is_correct_server_request(const communication::ServerRequest & request);
bool is_correct_submit_task(const communication::SubmitTask & task);
bool is_correct_subscribe(const communication::Subscribe & subscribe);
bool is_correct_list_tasks(const communication::ListTasks & list_tasks);

communication::WrapperMessage ServerResponse(google::protobuf::int64 request_id);
communication::WrapperMessage SubmitTaskResponse(google::protobuf::int64 request_id, google::protobuf::int32 submitted_task_id, communication::Status status);
communication::WrapperMessage SubmitTaskResponse(google::protobuf::int64 request_id, communication::Status status);
communication::WrapperMessage SubscribeResponse(google::protobuf::int64 request_id, google::protobuf::int64 value, communication::Status status);
communication::WrapperMessage SubscribeResponse(google::protobuf::int64 request_id, communication::Status status);
communication::WrapperMessage ListTaskResponse(google::protobuf::int64 request_id, communication::Status status, const std::vector<communication::ListTasksResponse_TaskDescription> & tasks);
communication::WrapperMessage ListTaskResponse(google::protobuf::int64 request_id, communication::Status status);
communication::ListTasksResponse_TaskDescription TaskDescription(int64_t task_id, google::protobuf::string client_id, const communication::Task & task, int64_t result); 
communication::ListTasksResponse_TaskDescription TaskDescription(int64_t task_id, google::protobuf::string client_id, const communication::Task & task); 
communication::WrapperMessage WaitMessage(boost::asio::ip::tcp::socket & sock);
void SendMessage(boost::asio::ip::tcp::socket & sock, const communication::WrapperMessage & message);

}

#endif //__PROTOCOL_COMMON__
