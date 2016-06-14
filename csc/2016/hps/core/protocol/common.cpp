#include "common.h"

namespace protocol {

bool is_request(const communication::WrapperMessage & message)
{
    return message.has_request() && !message.has_response();
}

bool is_correct_server_request(const communication::ServerRequest & request)
{
    return request.IsInitialized() && 
           ( (request.has_submit() && !request.has_subscribe() && !request.has_list())
            || !(request.has_submit() && request.has_subscribe() && !request.has_list())
            || !(request.has_submit() && !request.has_subscribe() && request.has_list()) );
}

bool is_correct_submit_task(const communication::SubmitTask & task)
{
    return task.IsInitialized();
}

bool is_correct_subscribe(const communication::Subscribe & subscribe)
{
    return subscribe.IsInitialized();
}

bool is_correct_list_tasks(const communication::ListTasks & list_tasks)
{
    return list_tasks.IsInitialized();
}

communication::WrapperMessage ServerResponse(google::protobuf::int64 request_id)
{
    communication::WrapperMessage message;
    message.mutable_response()->set_request_id(request_id);
    return message;
}

communication::WrapperMessage SubmitTaskResponse(google::protobuf::int64 request_id, google::protobuf::int32 submitted_task_id, communication::Status status)  
{
    communication::WrapperMessage message = ServerResponse(request_id);
    message.mutable_response()->mutable_submitresponse()->set_submittedtaskid(submitted_task_id);
    message.mutable_response()->mutable_submitresponse()->set_status(status);
    return message;
}

communication::WrapperMessage SubmitTaskResponse(google::protobuf::int64 request_id, communication::Status status)
{
    communication::WrapperMessage message = ServerResponse(request_id);
    message.mutable_response()->mutable_submitresponse()->set_status(status);
    return message;
}

communication::WrapperMessage SubscribeResponse(google::protobuf::int64 request_id, google::protobuf::int64 value, communication::Status status)
{
    communication::WrapperMessage message = ServerResponse(request_id);
    message.mutable_response()->mutable_subscriberesponse()->set_value(value);
    message.mutable_response()->mutable_subscriberesponse()->set_status(status);
    return message;
}

communication::WrapperMessage SubscribeResponse(google::protobuf::int64 request_id, communication::Status status)
{
    communication::WrapperMessage message = ServerResponse(request_id);
    message.mutable_response()->mutable_submitresponse()->set_status(status);
    return message;
}

communication::WrapperMessage ListTaskResponse(google::protobuf::int64 request_id, communication::Status status, const std::vector<communication::ListTasksResponse_TaskDescription> & tasks)
{
    communication::WrapperMessage message = ServerResponse(request_id);
    message.mutable_response()->mutable_listresponse()->set_status(status);
    for (auto & v : tasks) {
        *message.mutable_response()->mutable_listresponse()->add_tasks() = v;
    }
    return message;
}

communication::WrapperMessage ListTaskResponse(google::protobuf::int64 request_id, communication::Status status)
{
    communication::WrapperMessage message = ServerResponse(request_id);
    message.mutable_response()->mutable_listresponse()->set_status(status);
    return message;
}

communication::ListTasksResponse_TaskDescription TaskDescription(int64_t task_id, google::protobuf::string client_id, const communication::Task & task, int64_t result)
{
    communication::ListTasksResponse_TaskDescription description;
    description.set_taskid(task_id);
    description.set_clientid(client_id);
    *description.mutable_task() = task;
    description.set_result(result);
    return description;
}

communication::ListTasksResponse_TaskDescription TaskDescription(int64_t task_id, google::protobuf::string client_id, const communication::Task & task)
{
    communication::ListTasksResponse_TaskDescription description;
    description.set_taskid(task_id);
    description.set_clientid(client_id);
    *description.mutable_task() = task;
    return description;
}

communication::WrapperMessage WaitMessage(boost::asio::ip::tcp::socket & sock)
{
    boost::system::error_code ec;
    std::vector<char> size_str(4);
    int bytes = read(sock, boost::asio::buffer(size_str), boost::asio::transfer_exactly(4), ec);
    if (ec)
    {
        sock.close();
        //error
    }
    else
    {
          google::protobuf::uint32 size;
          {
              google::protobuf::io::ArrayInputStream ais(&size_str[0],4);
              google::protobuf::io::CodedInputStream coded_input(&ais);
              coded_input.ReadVarint32(&size); //Decode the HDR and get the size
          }

          {
              std::vector<char> body_str(size);
              read(sock, boost::asio::buffer(body_str), boost::asio::transfer_exactly(size), ec);
              body_str.insert(body_str.begin(), size_str.begin(), size_str.end());
              size += 4;
              if (!ec) {
                  communication::WrapperMessage message;
                  google::protobuf::io::ArrayInputStream ais(&body_str[0], size);
                  google::protobuf::io::CodedInputStream coded_input(&ais);
                  coded_input.ReadVarint32(&size);

                  google::protobuf::io::CodedInputStream::Limit msgLimit = coded_input.PushLimit(size);
                  
                  message.ParseFromCodedStream(&coded_input);
                  coded_input.PopLimit(msgLimit);
                  return message;
              } else {
                sock.close();
                   //error
              }
          }
    }
    return communication::WrapperMessage();
}

void SendMessage(boost::asio::ip::tcp::socket & sock, const communication::WrapperMessage & message) { 
    int size = message.ByteSize() + 4;
    std::vector<char> pkt(size);
    google::protobuf::io::ArrayOutputStream aos(&pkt[0], size);
    google::protobuf::io::CodedOutputStream *coded_output = new google::protobuf::io::CodedOutputStream(&aos);
    coded_output->WriteVarint32(message.ByteSize());
    message.SerializeToCodedStream(coded_output);
    try {
    sock.write_some(boost::asio::buffer(pkt)); //check rc
    } catch (std::exception & ex) {
    }
}

}
