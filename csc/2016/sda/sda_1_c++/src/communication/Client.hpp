#ifndef CLIENT
#define CLIENT

#include "SocketListenerBase.hpp"
#include "protocol.pb.h"

namespace communication {

class Client : public SocketListenerBase
{
public:
    typedef boost::system::error_code error_code;
    typedef boost::shared_ptr<Client> ptr;

    Client(io_service& service);

    static ptr get_new(io_service& service) {
        ptr new_(new Client(service));
        return new_;
    }

protected:
    virtual void on_read_end(const error_code & err, size_t bytes);
};


} // namespace communication

#endif /* end of include guard: Client */
