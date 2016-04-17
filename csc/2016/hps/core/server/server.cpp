#include <boost/asio.hpp>
#include <functional>
#include <exception>
#include <stdexcept>
#include <dlfcn.h>
#include "../metadata/protocol.pb.h"
#include <google/protobuf/message.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/text_format.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>
#include "RunningAndDoneQueue.h"
#include "AsyncService.h"
#include "TaskQueue.h"
#include "CommonThreadPool.h"

std::condition_variable _main_threads_cond_var;
boost::asio::io_service _io_service;
boost::asio::ip::tcp::acceptor _acceptor(_io_service, boost::asio::ip::tcp::endpoint(boost::asio::ip::tcp::v4(), 4911));    
TaskQueue _queue(_main_threads_cond_var);
RunningAndDoneQueue _running_and_done_queue;
CommonThreadPool _common_thread_pool(std::thread::hardware_concurrency(), _queue, _running_and_done_queue, _main_threads_cond_var); 

void handle_accept(AsyncService::ptr_on_async_service & client, const boost::system::error_code & err)
{
    if (!err) {
        client->start();
        AsyncService::ptr_on_async_service new_client = AsyncService::get_new_ptr_on_async_service(_io_service, _queue);
        _acceptor.async_accept(new_client->get_socket(), std::bind(handle_accept, new_client, std::placeholders::_1));
    }
}

int main( int argc, char ** argv )
{
    char * error;
    void * handle;
    int64_t (*task)(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n);

    handle = dlopen(APPS_LIBRARY_PATH, RTLD_LAZY);
    if (!handle) {
        fputs(dlerror(), stderr);
        exit(1);
    }

    task = (int64_t (*)(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n))dlsym(handle, "task");
    if ((error = dlerror()) != nullptr) {
        fputs(dlerror(), stderr);
        exit(1);
    }
    _common_thread_pool.set_calculation_function(task);

    GOOGLE_PROTOBUF_VERIFY_VERSION;
    AsyncService::ptr_on_async_service client = AsyncService::get_new_ptr_on_async_service(_io_service, _queue);
    _acceptor.async_accept(client->get_socket(), std::bind(handle_accept, client, std::placeholders::_1));
    _io_service.run();
    return 0;
}
