#include <boost/asio.hpp>
#include <boost/thread.hpp>
#include <algorithm>
#include <string>
#include <iostream>
#include <thread>
#include <system_error>
#include <exception>
#include <stdexcept>
#include "../metadata/protocol.pb.h"
#include <google/protobuf/message.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/text_format.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
#include "../protocol/common.h"
#include "../server/ValueHolder.h"

boost::asio::ip::tcp::endpoint ep(boost::asio::ip::address::from_string("127.0.0.1"), 4911);
boost::asio::io_service service;

struct ParamValue {
    ParamValue() {
        value = 0;
    }
    ParamValue(google::protobuf::int64 v) {
        value = v;
    }
    google::protobuf::int64 value;
};

struct ParamDependentTaskId {
    ParamDependentTaskId() {
        dependent_task_id = -1;
    }
    ParamDependentTaskId(google::protobuf::int64 v) {
        dependent_task_id = v;
    }
    google::protobuf::int32 dependent_task_id;
};


struct param_value_is_not_set : public std::exception
{
    virtual const char * what() const throw()
    {
        return "Parameter is not set";
    }
};

struct Param {

    Param(const ParamValue & value) {
        value_ = value;
    }

    Param(const ParamDependentTaskId & dependent_task_id) {
        dependent_task_id_ = dependent_task_id;
    }

    bool is_set_value() const {
        return value_;
    }

    bool is_set_dependent_task_id() {
        return dependent_task_id_;
    }
    
    ParamValue get_value() const {
        if (value_) {
            return value_.get();
        } else {
            throw param_value_is_not_set();
        }
    }

    ParamDependentTaskId get_dependent_task_id() const {
        if (dependent_task_id_) {
        return dependent_task_id_.get();
        } else {
            throw param_value_is_not_set();
        }
    }

    private:
        ValueHolder<ParamValue> value_;
        ValueHolder<ParamDependentTaskId> dependent_task_id_;
};

communication::WrapperMessage ServerRequest(const google::protobuf::string & client_id, google::protobuf::int64 request_id)
{
    communication::WrapperMessage message;
    message.mutable_request()->set_client_id(client_id);
    message.mutable_request()->set_request_id(request_id);
    return message;
}

communication::WrapperMessage SubmitTask(const google::protobuf::string & client_id, google::protobuf::int64 request_id, 
                                         const Param & a, const Param & b, 
                                         const Param & p, const Param & m,
                                         const google::protobuf::int64 & n)
{
    communication::WrapperMessage message = ServerRequest(client_id, request_id);
    
    (a.is_set_value() 
        ? message.mutable_request()->mutable_submit()->mutable_task()->mutable_a()->set_value(a.get_value().value) 
        : message.mutable_request()->mutable_submit()->mutable_task()->mutable_a()->set_dependenttaskid(a.get_dependent_task_id().dependent_task_id));

    (b.is_set_value() 
        ? message.mutable_request()->mutable_submit()->mutable_task()->mutable_b()->set_value(b.get_value().value) 
        : message.mutable_request()->mutable_submit()->mutable_task()->mutable_b()->set_dependenttaskid(b.get_dependent_task_id().dependent_task_id));
    
    (p.is_set_value() 
        ? message.mutable_request()->mutable_submit()->mutable_task()->mutable_p()->set_value(p.get_value().value) 
        : message.mutable_request()->mutable_submit()->mutable_task()->mutable_p()->set_dependenttaskid(p.get_dependent_task_id().dependent_task_id));

    (m.is_set_value() 
        ? message.mutable_request()->mutable_submit()->mutable_task()->mutable_m()->set_value(m.get_value().value) 
        : message.mutable_request()->mutable_submit()->mutable_task()->mutable_m()->set_dependenttaskid(m.get_dependent_task_id().dependent_task_id));

    message.mutable_request()->mutable_submit()->mutable_task()->set_n(n);

    return message; 
}

communication::WrapperMessage Subscribe(const google::protobuf::string & client_id , google::protobuf::int64 request_id, google::protobuf::int32 task_id)
{
    communication::WrapperMessage message = ServerRequest(client_id, request_id);
    message.mutable_request()->mutable_subscribe()->set_taskid(task_id);
    return message;
}

communication::WrapperMessage ListTasks(const google::protobuf::string & client_id , google::protobuf::int64 request_id)
{
    communication::WrapperMessage message = ServerRequest(client_id, request_id);
    message.mutable_request()->mutable_list();
    return message;
}

#define THREAD_SIZE 1000

int main( int argc, char ** argv )
{
    GOOGLE_PROTOBUF_VERIFY_VERSION;
    std::vector<std::thread> threads;
    threads.resize(THREAD_SIZE);
    for (int i = 0; i < THREAD_SIZE; i++) {
        threads[i] = std::thread([]() {
        boost::asio::ip::tcp::socket sock(service);
        sock.connect(ep);
        protocol::SendMessage(sock, SubmitTask("SubmitTask", rand(), ParamValue(rand()), ParamValue(rand()), ParamValue(rand()), ParamValue(rand()), rand() % 1000000000));
        std::cout << protocol::WaitMessage(sock).DebugString();
        protocol::SendMessage(sock, Subscribe("Subscribe", rand(), 0));
        std::cout << protocol::WaitMessage(sock).DebugString();
        protocol::SendMessage(sock, SubmitTask("SubmitTask", rand(), ParamDependentTaskId(rand() % THREAD_SIZE), ParamValue(rand()), ParamValue(rand()), ParamValue(rand()), rand() % 1000000000));
        std::cout << protocol::WaitMessage(sock).DebugString();
        protocol::SendMessage(sock, SubmitTask("SubmitTask", rand(), ParamDependentTaskId(rand() % THREAD_SIZE), ParamValue(rand()), ParamValue(rand()), ParamValue(rand()), rand() % 1000000000));
        std::cout << protocol::WaitMessage(sock).DebugString();
        protocol::SendMessage(sock, ListTasks("ListTasks", rand()));
        std::cout << protocol::WaitMessage(sock).DebugString();
        sock.close(); 
        });
    }
    for (int i = 0; i < THREAD_SIZE; i++) {
        threads[i].join();
    }

    return 0;
}

