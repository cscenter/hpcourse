#include "data_handler.hpp"
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>

data_handler::data_handler() {}

communication::WrapperMessage data_handler::parse(char const *data, size_t const len)
{
    google::protobuf::io::ArrayInputStream array_input(data, len);
    google::protobuf::io::CodedInputStream coded_input(&array_input);

    uint32_t msg_len = 0;
    coded_input.ReadVarint32(&msg_len);
    auto limit = coded_input.PushLimit(msg_len);

    communication::WrapperMessage msg_req;

    msg_req.ParseFromCodedStream(&coded_input);
    coded_input.PopLimit(limit);

    return msg_req;
}

int data_handler::serialize(communication::WrapperMessage const &msg, char *data, size_t max_len) {

    google::protobuf::io::ArrayOutputStream array_output(data, max_len);
    google::protobuf::io::CodedOutputStream coded_output(&array_output);

    coded_output.WriteVarint32(msg.ByteSize());
    msg.SerializeToCodedStream(&coded_output);

    return coded_output.ByteCount();
}
