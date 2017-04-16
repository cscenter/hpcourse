#ifndef SERVER
#define SERVER

#include "SocketListenerBase.hpp"
#include "ServerTask.hpp"
#include "protocol.pb.h"

namespace communication {

class Server : public SocketListenerBase
{
public:
    typedef boost::shared_ptr<Server> ptr;

    Server(io_service& service);

    void process_task(ServerRequest & submit);
    void process_subscription(ServerRequest & subscribe);
    void process_get_list(ServerRequest & subscribe);

    static ptr get_new(io_service& service) {
        ptr new_(new Server(service));
        return new_;
    }

protected:
    virtual void on_read_end(const error_code & err, size_t bytes);

}; // Server


} // namespace communication


#endif /* end of include guard: Server */
