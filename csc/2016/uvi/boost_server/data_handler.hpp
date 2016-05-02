#ifndef BOOST_SERVER_REQUEST_PARSER_HPP
#define BOOST_SERVER_REQUEST_PARSER_HPP

#include "protocol.pb.h"

struct data_handler
{
public:
    data_handler();
    data_handler(data_handler const&) = delete;
    data_handler& operator=(data_handler const&) = delete;

    communication::WrapperMessage parse(char const* data, size_t const len);
    int serialize(communication::WrapperMessage const& msg, char* data, size_t const max_len);

private:


};

#endif //BOOST_SERVER_REQUEST_PARSER_HPP
